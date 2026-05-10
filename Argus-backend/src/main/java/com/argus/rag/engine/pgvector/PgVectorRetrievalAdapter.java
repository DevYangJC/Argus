package com.argus.rag.engine.pgvector;

import com.argus.rag.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * PgVector 向量检索适配器，封装 Spring AI {@link VectorStore} 抽象层，
 * 提供基于语义相似度的文档切片召回能力。
 * <p>
 * <b>定位：</b>RAG 管道中「向量语义检索」路的统一入口。
 * 底层向量数据库（PgVector）通过 Spring AI 的 {@link VectorStore} 接口屏蔽差异，
 * 调用方无需关心具体实现。
 * <p>
 * <b>安全约束：</b>
 * <ul>
 *   <li>检索请求通过 {@code FilterExpression} 强制添加 {@code groupId} 过滤，
 *       确保只搜索当前群组内的文档切片。</li>
 *   <li>结果返回后二次校验每条结果的 {@code groupId}，
 *       若与请求不一致则抛出异常（防御式编程，杜绝向量数据库 Bug 导致的数据泄露）。</li>
 * </ul>
 * <p>
 * <b>元数据兼容性：</b>{@link #requireLong} 和 {@link #requireInteger} 同时兼容
 * {@link Number} 和 {@link String} 两种类型的元数据值，
 * 适配不同向量数据库驱动可能产生的类型差异。
 *
 * @author Argus-RAG Team
 * @see VectorStore           Spring AI 向量存储抽象
 * @see VectorHit             检索命中结果记录
 */
@Component
@Slf4j
public class PgVectorRetrievalAdapter {

    /** Spring AI 向量存储实例，负责与底层 PgVector 交互 */
    private final VectorStore vectorStore;

    /**
     * 通过构造注入接收 {@link VectorStore} Bean。
     *
     * @param vectorStore Spring AI 自动配置的向量存储实例
     */
    public PgVectorRetrievalAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 执行向量语义检索，返回与问题语义最相似的 topK 个文档切片。
     * <p>
     * 检索过程：
     * <ol>
     *   <li>构建 {@link SearchRequest}：问题文本 + topK + groupId 过滤</li>
     *   <li>调用 {@link VectorStore#similaritySearch} 执行向量相似度计算</li>
     *   <li>将每条 {@link Document} 结果转换为类型安全的 {@link VectorHit}</li>
     *   <li>转换过程中校验 groupId 一致性（防御跨群组数据泄露）</li>
     * </ol>
     *
     * @param groupId  群组 ID，用于限定检索范围
     * @param question 用户问题文本（会被向量化后检索）
     * @param topK     返回的最大结果数
     * @return 语义最相似的切片列表，按相似度降序排列；无结果时返回空列表
     * @throws BusinessException 元数据缺失、格式非法或跨群组数据检测到时抛出
     */
    public List<VectorHit> search(Long groupId, String question, int topK) {
        long startNano = System.nanoTime();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("groupId", groupId).build())
                .build();
        List<VectorHit> hits = vectorStore.similaritySearch(searchRequest).stream()
                .map(document -> toVectorHit(groupId, document))
                .toList();
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
        log.info("向量检索完成: groupId={}, topK={}, hitCount={}, elapsedMs={}",
                groupId, topK, hits.size(), elapsedMs);
        return hits;
    }

    /**
     * 将 Spring AI {@link Document} 转换为领域专用的 {@link VectorHit} 记录。
     * <p>
     * 执行以下安全检查：
     * <ul>
     *   <li>校验返回结果的 {@code groupId} 与请求一致（防御向量数据库误返回）</li>
     *   <li>确保 {@code chunkText} 非空（空切片无业务价值）</li>
     *   <li>处理 {@code score} 为 null 的边界情况（降级为 0）</li>
     * </ul>
     *
     * @param expectedGroupId 期望的群组 ID（来自检索请求）
     * @param document        Spring AI 返回的文档对象
     * @return 类型安全的检索命中记录
     * @throws BusinessException groupId 不一致、元数据缺失或格式非法时抛出
     */
    private VectorHit toVectorHit(Long expectedGroupId, Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Long groupId = requireLong(metadata, "groupId");
        if (!expectedGroupId.equals(groupId)) {
            throw new BusinessException("向量检索返回了跨群组数据");
        }
        return new VectorHit(
                requireLong(metadata, "documentId"),
                requireLong(metadata, "chunkId"),
                requireInteger(metadata, "chunkIndex"),
                requireText(document.getText()),
                document.getScore() == null ? 0D : document.getScore()
        );
    }

    /**
     * 从元数据 Map 中安全提取 {@link Long} 类型值。
     * <p>
     * 兼容两种常见的元数据类型：
     * <ul>
     *   <li>{@link Number}：直接调用 {@code longValue()} 转换</li>
     *   <li>{@link String}：尝试 {@link Long#parseLong} 解析</li>
     * </ul>
     *
     * @param metadata 元数据 Map
     * @param key      元数据键名
     * @return 解析后的 Long 值
     * @throws BusinessException 键不存在、值为空或格式非法时抛出
     */
    private Long requireLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException("向量检索元数据格式非法: " + key, exception);
            }
        }
        throw new BusinessException("向量检索缺少必要元数据: " + key);
    }

    /**
     * 从元数据 Map 中安全提取 {@link Integer} 类型值。
     * <p>
     * 与 {@link #requireLong} 逻辑一致，适配 {@link Number} 和 {@link String} 两种类型。
     *
     * @param metadata 元数据 Map
     * @param key      元数据键名
     * @return 解析后的 Integer 值
     * @throws BusinessException 键不存在、值为空或格式非法时抛出
     */
    private Integer requireInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException("向量检索元数据格式非法: " + key, exception);
            }
        }
        throw new BusinessException("向量检索缺少必要元数据: " + key);
    }

    /**
     * 校验切片文本非空，返回去除首尾空白后的文本。
     *
     * @param text 原始文本
     * @return 去除首尾空白后的文本
     * @throws BusinessException 文本为空或仅含空白字符时抛出
     */
    private String requireText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("向量检索返回空切片");
        }
        return text.trim();
    }

    /**
     * 向量检索的命中结果记录。
     * <p>
     * 使用 Java {@code record} 保证不可变性和值语义，
     * 可直接用于下游排序、融合和 LLM 上下文组装。
     *
     * @param documentId 所属文档 ID
     * @param chunkId    切片 ID
     * @param chunkIndex 切片在文档中的序号（从 0 开始）
     * @param chunkText  切片文本内容
     * @param score      语义相似度分数（余弦相似度，范围 [0, 1]）
     */
    public record VectorHit(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String chunkText,
            double score
    ) {
    }
}
