package com.argus.rag.engine.elasticsearch;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 文档切片索引服务，兼具 <b>索引管理</b> 和 <b>关键词检索</b> 两大职责。
 * <p>
 * <b>职责一：索引管理（写入）</b>
 * <ul>
 *   <li>将文档切片写入 ES 索引（{@link #indexReadyChunks}）</li>
 *   <li>按 documentId 批量删除过期索引（{@link #deleteDocumentChunks}）</li>
 *   <li>启动时自动检测并创建索引（{@link #ensureIndexInitialized}）</li>
 * </ul>
 * <p>
 * <b>职责二：关键词检索（召回）</b>
 * <ul>
 *   <li>基于 IK 中文分词的全文本检索（{@link #search}）</li>
 *   <li>双层打分：bool 初排 + rescore 二次精排</li>
 *   <li>多字段加权：fileName 和 chunkText 各有 match_phrase / match 两种匹配策略</li>
 *   <li>分数归一化：将对数变换后的原始分数压缩到 [0, 1] 区间</li>
 * </ul>
 * <p>
 * <b>检索安全保障：</b>
 * <ul>
 *   <li>filter 层强制过滤 groupId + status=READY + deleted=false</li>
 *   <li>检索失败不抛异常，降级返回空列表（保证服务不中断）</li>
 * </ul>
 * <p>
 * <b>通信方式：</b>通过 JDK 原生 {@link HttpClient} 直接调用 ES REST API，
 * 无第三方 ES 客户端依赖。
 *
 * @author Argus-RAG Team
 * @see KeywordHit 关键词检索命中结果记录
 */
@Service
@Slf4j
public class ElasticsearchChunkIndexService {


    /** HTTP 请求超时时间，平衡响应速度和 ES 集群压力 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** 关键词分数归一化的参考基准值，用于对数变换 */
    private static final double KEYWORD_SCORE_REFERENCE = 100D;

    /** ES 索引中文档切片的有效状态标识 */
    private static final String READY_STATUS = "READY";

    /** JSON 序列化 / 反序列化工具 */
    private final ObjectMapper objectMapper;

    /** JDK 原生 HTTP 客户端，复用连接池 */
    private final HttpClient httpClient;

    /** ES 基础 URL（scheme + host + port），如 {@code http://localhost:9200} */
    private final String baseUrl;

    /** ES 索引名称 */
    private final String indexName;

    /** 索引是否已初始化（volatile 保证多线程可见性） */
    private volatile boolean indexInitialized;

    /**
     * Spring 构造注入，通过 {@code @Value} 读取 ES 连接配置。
     *
     * @param objectMapper Jackson ObjectMapper
     * @param host         ES 主机地址，默认 {@code localhost}
     * @param port         ES 端口，默认 {@code 9200}
     * @param scheme       ES 协议，默认 {@code http}
     * @param indexName    ES 索引名称，默认 {@code argus_rag_document_chunks}
     */
    @Autowired
    public ElasticsearchChunkIndexService(
            ObjectMapper objectMapper,
            @Value("${elasticsearch.host:localhost}") String host,
            @Value("${elasticsearch.port:9200}") int port,
            @Value("${elasticsearch.scheme:http}") String scheme,
            @Value("${elasticsearch.index-name:argus_rag_document_chunks}") String indexName
    ) {
        this(
                objectMapper,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
                host,
                port,
                scheme,
                indexName
        );
    }

    /**
     * 包私有构造器，用于测试时注入 mock {@link HttpClient}。
     *
     * @param objectMapper Jackson ObjectMapper
     * @param httpClient   JDK HTTP 客户端实例
     * @param host         ES 主机
     * @param port         ES 端口
     * @param scheme       ES 协议
     * @param indexName    ES 索引名称
     */
    ElasticsearchChunkIndexService(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String host,
            int port,
            String scheme,
            String indexName
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.baseUrl = "%s://%s:%d".formatted(scheme, host, port);
        this.indexName = indexName;
    }

    /**
     * 将一批文档切片写入 ES 索引，用于后续关键词检索。
     * <p>
     * 内部逐条写入，每条切片调用 {@link #indexChunk} 完成序列化和 PUT。
     *
     * @param fileName 原始文件名（写入 ES 供检索时展示）
     * @param chunks   文档切片列表（必须已包含 chunkId / groupId / documentId 等字段）
     */
    public void indexReadyChunks(String fileName, List<DocumentChunkEntity> chunks) {
        if (!StringUtils.hasText(fileName) || chunks == null || chunks.isEmpty()) {
            return;
        }
        ensureIndexInitialized();
        for (DocumentChunkEntity chunk : chunks) {
            indexChunk(fileName, chunk);
        }
    }

    /**
     * 按文档 ID 批量删除该文档在 ES 中的所有切片索引。
     * <p>
     * 使用 ES 的 {@code _delete_by_query} API，条件为 {@code term: {documentId: xxx}}。
     * 删除失败时记录 WARN 日志但不抛异常（索引删除不是关键路径）。
     *
     * @param documentId 要清理的文档 ID
     */
    public void deleteDocumentChunks(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        ensureIndexInitialized();
        String path = "/%s/_delete_by_query".formatted(indexName);
        Map<String, Object> requestBody = Map.of(
                "query", Map.of("term", Map.of("documentId", documentId))
        );
        try {
            sendJsonRequest("POST", path, requestBody, true);
            log.info("ES 文档切片索引删除完成: documentId={}", documentId);
        } catch (RuntimeException exception) {
            log.warn("ES 文档切片索引删除失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
    }

    /**
     * 执行 ES 关键词全文检索，返回与问题最匹配的 topK 个文档切片。
     * <p>
     * <b>检索策略：</b>
     * <ol>
     *   <li><b>Filter 过滤层：</b>限定 groupId + status=READY + deleted=false</li>
     *   <li><b>Should 召回层：</b>同时对 fileName 和 chunkText 做
     *       match_phrase（短语匹配，boost 6~8）和 match（分词匹配，boost 3~4）</li>
     *   <li><b>Rescore 精排层：</b>用更严格的 operator=and 算子做二次打分，
     *       修正初排的位置偏差</li>
     *   <li><b>分数归一化：</b>通过 {@link #normalizeKeywordScore} 将对数变换后的分数
     *       压缩到 [0, 1] 区间</li>
     * </ol>
     * <p>
     * <b>降级策略：</b>检索失败（ES 宕机、网络超时等）不抛异常，
     * 记录 WARN 日志后返回空列表，保证服务整体可用。
     *
     * @param groupId  群组 ID
     * @param question 检索关键词 / 问题文本
     * @param topK     返回的最大结果数
     * @return 关键词匹配的切片列表（含归一化分数），无结果或失败时返回空列表
     */
    public List<KeywordHit> search(Long groupId, String question, int topK) {
        if (groupId == null || groupId <= 0 || !StringUtils.hasText(question) || topK <= 0) {
            return List.of();
        }
        ensureIndexInitialized();
        long startNano = System.nanoTime();
        Map<String, Object> requestBody = buildKeywordSearchRequestBody(groupId, question, topK);
        try {
            JsonNode root = sendJsonRequest("POST", "/%s/_search".formatted(indexName), requestBody, true);
            JsonNode hitsNode = root.path("hits").path("hits");
            if (!hitsNode.isArray() || hitsNode.isEmpty()) {
                long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                log.info("关键词检索完成: groupId={}, topK={}, hitCount=0, elapsedMs={}", groupId, topK, elapsedMs);
                return List.of();
            }
            List<KeywordHit> hits = new ArrayList<>();
            for (JsonNode hitNode : hitsNode) {
                JsonNode sourceNode = hitNode.path("_source");
                double rawScore = hitNode.path("_score").asDouble(0D);
                hits.add(new KeywordHit(
                        sourceNode.path("documentId").asLong(),
                        sourceNode.path("chunkId").asLong(),
                        sourceNode.path("chunkIndex").asInt(),
                        sourceNode.path("fileName").asText(""),
                        sourceNode.path("chunkText").asText(""),
                        rawScore,
                        normalizeKeywordScore(rawScore)
                ));
            }
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            log.info("关键词检索完成: groupId={}, topK={}, hitCount={}, elapsedMs={}",
                    groupId, topK, hits.size(), elapsedMs);
            return List.copyOf(hits);
        } catch (RuntimeException exception) {
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            log.warn(
                    "ES 关键词检索失败，降级为空结果: groupId={}, question='{}', elapsedMs={}, reason={}",
                    groupId,
                    abbreviate(question),
                    elapsedMs,
                    exception.getMessage()
            );
            return List.of();
        }
    }

    /**
     * 将单条文档切片写入 ES。
     * <p>
     * 写入前调用 {@link #validateChunk} 校验必要字段完整性。
     * 使用 {@code PUT /index/_doc/{chunkId}} 确保相同 chunkId 幂等覆盖。
     *
     * @param fileName 原始文件名
     * @param chunk    文档切片实体
     */
    private void indexChunk(String fileName, DocumentChunkEntity chunk) {
        validateChunk(chunk);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("chunkId", chunk.getId());
        requestBody.put("groupId", chunk.getGroupId());
        requestBody.put("documentId", chunk.getDocumentId());
        requestBody.put("chunkIndex", chunk.getChunkIndex());
        requestBody.put("fileName", fileName);
        requestBody.put("chunkText", chunk.getChunkText());
        requestBody.put("status", READY_STATUS);
        requestBody.put("deleted", false);
        sendJsonRequest(
                "PUT",
                "/%s/_doc/%s".formatted(indexName, URLEncoder.encode(String.valueOf(chunk.getId()), StandardCharsets.UTF_8)),
                requestBody,
                false
        );
    }

    /**
     * 校验文档切片的必要字段是否完整。
     * <p>
     * 检查项：chunk 非 null、id/groupId/documentId/chunkIndex 非 null、chunkText 非空。
     *
     * @param chunk 文档切片实体
     * @throws BusinessException 任一必要字段缺失时抛出
     */
    private void validateChunk(DocumentChunkEntity chunk) {
        if (chunk == null
                || chunk.getId() == null
                || chunk.getGroupId() == null
                || chunk.getDocumentId() == null
                || chunk.getChunkIndex() == null
                || !StringUtils.hasText(chunk.getChunkText())) {
            throw new BusinessException("ES 索引写入缺少必要 chunk 字段");
        }
    }

    /**
     * 确保 ES 索引已就绪——不存在则自动创建。
     * <p>
     * 采用「双重检查锁定 + volatile」模式：
     * <ol>
     *   <li>volatile 读快速路径：{@code indexInitialized} 为 true 直接返回</li>
     *   <li>获取 {@code synchronized(this)} 锁</li>
     *   <li>二次检查 volatile 变量（防止竞态窗口期重复初始化）</li>
     *   <li>调用 {@link #indexExists} 检查，不存在则 {@link #createIndex}</li>
     *   <li>置位 {@code indexInitialized = true}</li>
     * </ol>
     */
    private void ensureIndexInitialized() {
        if (indexInitialized) {
            return;
        }
        synchronized (this) {
            if (indexInitialized) {
                return;
            }
            if (!indexExists()) {
                createIndex();
            }
            indexInitialized = true;
        }
    }

    /**
     * 检查 ES 索引是否存在。
     * <p>
     * 发送 HEAD 请求到 ES，200 表示存在，404 表示不存在，其他状态码视为检查失败。
     * 正确处理了线程中断信号（恢复中断状态后抛异常）。
     *
     * @return {@code true} 索引已存在，{@code false} 索引不存在
     * @throws BusinessException ES 通信失败或返回非预期状态码时抛出
     */
    private boolean indexExists() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/%s".formatted(indexName)))
                    .timeout(REQUEST_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 404) {
                return false;
            }
            throw new BusinessException("ES 索引检查失败: " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ES 索引检查失败", exception);
        } catch (IOException exception) {
            throw new BusinessException("ES 索引检查失败", exception);
        }
    }

    /**
     * 创建 ES 索引，使用自定义的 IK 中文分词器配置。
     * <p>
     * 调用 {@link #buildCreateIndexRequestBody} 生成索引定义 JSON，
     * 包含 mappings 字段类型定义和 analysis 分词器配置。
     */
    private void createIndex() {
        sendJsonRequest("PUT", "/%s".formatted(indexName), buildCreateIndexRequestBody(), false);
        log.info("ES 索引初始化完成: {}", indexName);
    }

    /**
     * 构建 ES 索引创建请求体（settings + mappings）。
     * <p>
     * <b>Analysis 配置：</b>
     * <ul>
     *   <li>{@code argus_rag_ik_index} —— 索引端使用 IK 最大切分（ik_max_word），提高召回覆盖率</li>
     *   <li>{@code argus_rag_ik_search} —— 查询端使用 IK 智能切分（ik_smart），提高精确度</li>
     * </ul>
     * <p>
     * <b>Mappings 字段：</b>
     * <ul>
     *   <li>{@code groupId / documentId / chunkId} —— long 类型（精确匹配 + filter）</li>
     *   <li>{@code chunkIndex} —— integer 类型</li>
     *   <li>{@code status} —— keyword 类型（精确 term 过滤）</li>
     *   <li>{@code deleted} —— boolean 类型</li>
     *   <li>{@code fileName} —— text 类型，双字段（原字段 + keyword 子字段）</li>
     *   <li>{@code chunkText} —— text 类型，IK 中文分词</li>
     * </ul>
     * <p>
     * <b>可见性：</b>包私有，供单元测试验证索引定义。
     *
     * @return ES 索引创建请求体 Map
     */
    Map<String, Object> buildCreateIndexRequestBody() {
        return Map.of(
                "settings", Map.of(
                        "analysis", Map.of(
                                "analyzer", Map.of(
                                        "argus_rag_ik_index", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_max_word"
                                        ),
                                        "argus_rag_ik_search", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_smart"
                                        )
                                )
                        )
                ),
                "mappings", Map.of(
                        "properties", Map.of(
                                "groupId", Map.of("type", "long"),
                                "documentId", Map.of("type", "long"),
                                "chunkId", Map.of("type", "long"),
                                "chunkIndex", Map.of("type", "integer"),
                                "status", Map.of("type", "keyword"),
                                "deleted", Map.of("type", "boolean"),
                                "fileName", Map.of(
                                        "type", "text",
                                        "analyzer", "argus_rag_ik_index",
                                        "search_analyzer", "argus_rag_ik_search",
                                        "fields", Map.of(
                                                "keyword", Map.of(
                                                        "type", "keyword",
                                                        "ignore_above", 256
                                                )
                                        )
                                ),
                                "chunkText", Map.of(
                                        "type", "text",
                                        "analyzer", "argus_rag_ik_index",
                                        "search_analyzer", "argus_rag_ik_search"
                                )
                        )
                )
        );
    }

    /**
     * 构建 ES 关键词检索请求体（两阶段打分）。
     * <p>
     * <b>结构：</b>
     * <pre>
     * {
     *   "size": topK,
     *   "_source": ["groupId", "documentId", "chunkId", "chunkIndex", "fileName", "chunkText"],
     *   "query": { bool: { filter: [...], should: [...], minimum_should_match: 1 } },
     *   "rescore": { window_size: topK, query: { ... } }
     * }
     * </pre>
     * <p>
     * <b>可见性：</b>包私有，供单元测试验证检索 DSL。
     *
     * @param groupId  群组 ID
     * @param question 检索文本
     * @param topK     返回上限
     * @return ES 检索请求体 Map
     */
    Map<String, Object> buildKeywordSearchRequestBody(Long groupId, String question, int topK) {
        Map<String, Object> boolQuery = Map.of(
                "filter", List.of(
                        Map.of("term", Map.of("groupId", groupId)),
                        Map.of("term", Map.of("status", READY_STATUS)),
                        Map.of("term", Map.of("deleted", false))
                ),
                "should", buildKeywordShouldClauses(question),
                "minimum_should_match", 1
        );
        return Map.of(
                "size", topK,
                "_source", List.of("groupId", "documentId", "chunkId", "chunkIndex", "fileName", "chunkText"),
                "query", Map.of("bool", boolQuery),
                "rescore", Map.of(
                        "window_size", topK,
                        "query", Map.of(
                                "query_weight", 0.2D,
                                "rescore_query_weight", 1.0D,
                                "score_mode", "total",
                                "rescore_query", Map.of(
                                        "bool", Map.of(
                                                "should", buildKeywordRescoreShouldClauses(question),
                                                "minimum_should_match", 1
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 构建 ES bool query 中 should 子句列表（初排阶段）。
     * <p>
     * 四个打分维度：
     * <ol>
     *   <li>fileName 短语匹配——boost 8（文档名精确命中权重最高）</li>
     *   <li>fileName 分词匹配——boost 4（文档名模糊命中）</li>
     *   <li>chunkText 短语匹配——boost 6（内容精确命中）</li>
     *   <li>chunkText 分词匹配——boost 3（内容模糊命中）</li>
     * </ol>
     *
     * @param question 检索关键词
     * @return should 子句列表
     */
    private List<Map<String, Object>> buildKeywordShouldClauses(String question) {
        return List.of(
                Map.of("match_phrase", Map.of("fileName", Map.of("query", question, "boost", 8))),
                Map.of("match", Map.of("fileName", Map.of("query", question, "boost", 4))),
                Map.of("match_phrase", Map.of("chunkText", Map.of("query", question, "boost", 6))),
                Map.of("match", Map.of("chunkText", Map.of("query", question, "boost", 3)))
        );
    }

    /**
     * 构建 ES rescore 阶段的 should 子句列表（精排阶段）。
     * <p>
     * 与初排 {@link #buildKeywordShouldClauses} 的区别：
     * match 子句使用 {@code operator=and} 更严格要求所有词都匹配，
     * 确保精排阶段返回的结果精确度更高。
     *
     * @param question 检索关键词
     * @return rescore should 子句列表
     */
    private List<Map<String, Object>> buildKeywordRescoreShouldClauses(String question) {
        return List.of(
                Map.of("match_phrase", Map.of("fileName", Map.of("query", question, "boost", 8))),
                Map.of("match", Map.of("fileName", Map.of("query", question, "operator", "and", "boost", 5))),
                Map.of("match_phrase", Map.of("chunkText", Map.of("query", question, "boost", 7))),
                Map.of("match", Map.of("chunkText", Map.of("query", question, "operator", "and", "boost", 4)))
        );
    }

    /**
     * 发送 JSON 请求到 ES 并解析响应。
     * <p>
     * <b>参数说明：</b>
     * <ul>
     *   <li>{@code method} —— HTTP 方法（GET / PUT / POST / DELETE）</li>
     *   <li>{@code path} —— ES API 路径（如 {@code /index-name/_search}）</li>
     *   <li>{@code requestBody} —— 请求体 Map，会被序列化为 JSON</li>
     *   <li>{@code ignoreMissingIndex} —— 为 {@code true} 时，
     *       若 ES 返回 404（索引不存在）则返回空 JSON 对象而非抛异常</li>
     * </ul>
     * <p>
     * 正确处理了线程中断（恢复中断状态）。
     *
     * @param method             HTTP 方法
     * @param path               ES API 路径
     * @param requestBody        请求体 Map
     * @param ignoreMissingIndex 是否忽略索引不存在的 404 错误
     * @return ES 响应的 JSON 树
     * @throws BusinessException HTTP 通信失败、ES 返回错误状态码或 JSON 解析失败时抛出
     */
    private JsonNode sendJsonRequest(
            String method,
            String path,
            Map<String, Object> requestBody,
            boolean ignoreMissingIndex
    ) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (ignoreMissingIndex && response.statusCode() == 404) {
                return objectMapper.createObjectNode();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("ES 请求失败: " + response.statusCode() + ", body=" + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ES 请求失败", exception);
        } catch (IOException exception) {
            throw new BusinessException("ES 请求失败", exception);
        }
    }

    /**
     * 对日志输出中的问题文本做截断处理，防止超长文本撑爆日志。
     * <p>
     * 规则：合并连续空白 → 超过 120 字符则截断并追加 "..." → 否则原样返回。
     *
     * @param text 原始文本
     * @return 截断后的文本（不超过 123 字符）
     */
    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    /**
     * 将 ES 原始相关性分数归一化到 [0, 1] 区间。
     * <p>
     * 使用对数变换 {@code log1p(x)} 压缩分数范围，消除不同查询间分数不可比的问题。
     * 公式：{@code min(1.0, log1p(rawScore) / log1p(KEYWORD_SCORE_REFERENCE))}
     * <p>
     * 如 rawScore=100 时归一化分数约为 1.0，rawScore=10 时约为 0.5。
     *
     * @param rawScore ES 返回的原始 _score（非负）
     * @return 归一化后的分数 [0, 1]
     */
    private double normalizeKeywordScore(double rawScore) {
        if (rawScore <= 0D) {
            return 0D;
        }
        return Math.min(1D, Math.log1p(rawScore) / Math.log1p(KEYWORD_SCORE_REFERENCE));
    }

    /**
     * ES 关键词检索的命中结果记录。
     * <p>
     * 不可变 record，可安全用于下游排序、融合和 LLM 上下文组装。
     * <p>
     * {@code rawScore} 为 ES 返回的原始 BM25 分数（无上界），
     * {@code normalizedScore} 为经 {@link #normalizeKeywordScore} 归一化后的 [0, 1] 分数。
     *
     * @param documentId      所属文档 ID
     * @param chunkId         切片 ID
     * @param chunkIndex      切片序号（从 0 开始）
     * @param fileName        原始文件名
     * @param chunkText       切片文本内容
     * @param rawScore        ES 原始 BM25 分数
     * @param normalizedScore 归一化后的 [0, 1] 分数
     */
    public record KeywordHit(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            String chunkText,
            double rawScore,
            double normalizedScore
    ) {
    }
}
