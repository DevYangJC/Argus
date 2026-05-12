package com.argus.rag.ingestion.service.pipeline;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.ingestion.mapper.DocumentChunkMapper;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档切片持久化服务。
 *
 * <p>负责将 Spring AI {@link Document} 切片列表转换为 {@link DocumentChunkEntity}，
 * 删除旧切片后批量写入数据库，并回填自增主键。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class ChunkService {

    /** 切片摘要最大长度 */
    private static final int CHUNK_SUMMARY_LENGTH = 120;
    /** 默认切片策略标识 */
    private static final String DEFAULT_CHUNK_STRATEGY = "spring-ai-document";
    /** 空切片错误消息 */
    private static final String EMPTY_CHUNK_DOCUMENTS_MESSAGE = "文档切片结果为空，无法持久化";
    /** 每条 INSERT 记录占用的参数个数 */
    private static final int INSERT_BATCH_PARAMETER_COUNT = 10;
    /** PostgreSQL 参数上限 */
    private static final int POSTGRES_PARAMETER_LIMIT = 65_535;
    /** 单批 INSERT 最大条数，受 {@code POSTGRES_PARAMETER_LIMIT} 限制。保留 128 个参数余量 */
    private static final int MAX_INSERT_BATCH_SIZE = (POSTGRES_PARAMETER_LIMIT - 128) / INSERT_BATCH_PARAMETER_COUNT;

    /** 切片 Mapper */
    private final DocumentChunkMapper documentChunkMapper;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * @param documentChunkMapper 切片持久化 Mapper
     * @param objectMapper        JSON 序列化工具
     */
    public ChunkService(DocumentChunkMapper documentChunkMapper, ObjectMapper objectMapper) {
        this.documentChunkMapper = documentChunkMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存文档切片：校验参数、构建实体、删除旧数据、批量入库后回填主键。
     *
     * @param documentId 文档 ID
     * @param groupId    知识库 ID
     * @param documents  Spring AI 切片文档列表
     * @return 已持久化的切片实体列表（含自增主键）
     * @throws BusinessException 切片列表为空或回填主键失败时抛出
     */
    @Transactional
    public List<DocumentChunkEntity> saveChunkDocuments(Long documentId, Long groupId, List<Document> documents) {
        validateIdentifiers(documentId, groupId);
        List<DocumentChunkEntity> chunks = buildChunkDocuments(documentId, groupId, documents);
        if (chunks.isEmpty()) {
            throw new BusinessException(EMPTY_CHUNK_DOCUMENTS_MESSAGE);
        }
        log.info("开始保存切片: documentId={}, groupId={}, chunkCount={}", documentId, groupId, chunks.size());
        documentChunkMapper.deleteByDocumentId(documentId);
        persistChunkBatches(chunks);
        backfillChunkIds(documentId, chunks);
        log.info("切片保存完成: documentId={}, groupId={}, chunkCount={}", documentId, groupId, chunks.size());
        return chunks;
    }

    /** 按 PostgreSQL 参数上限分批入库，避免单条 SQL 超长 */
    private void persistChunkBatches(List<DocumentChunkEntity> chunks) {
        if (chunks.size() <= MAX_INSERT_BATCH_SIZE) {
            documentChunkMapper.insertBatch(chunks);
            return;
        }
        for (int start = 0; start < chunks.size(); start += MAX_INSERT_BATCH_SIZE) {
            int end = Math.min(start + MAX_INSERT_BATCH_SIZE, chunks.size());
            documentChunkMapper.insertBatch(chunks.subList(start, end));
        }
    }

    /** 从数据库反查自增主键并按 {@code chunkIndex} 回填到内存实体 */
    private void backfillChunkIds(Long documentId, List<DocumentChunkEntity> chunks) {
        if (chunks.stream().allMatch(chunk -> chunk.getId() != null)) {
            return;
        }
        List<DocumentChunkEntity> persistedChunks = documentChunkMapper.selectByDocumentId(documentId);
        if (persistedChunks.size() != chunks.size()) {
            throw new BusinessException("批量保存切片后回填主键失败");
        }
        Map<Integer, Long> chunkIdByIndex = new HashMap<>();
        for (DocumentChunkEntity persistedChunk : persistedChunks) {
            if (persistedChunk.getChunkIndex() == null || persistedChunk.getId() == null) {
                throw new BusinessException("批量保存切片后返回的主键数据不完整");
            }
            chunkIdByIndex.put(persistedChunk.getChunkIndex(), persistedChunk.getId());
        }
        for (DocumentChunkEntity chunk : chunks) {
            Long chunkId = chunkIdByIndex.get(chunk.getChunkIndex());
            if (chunkId == null) {
                throw new BusinessException("批量保存切片后无法匹配 chunk 主键");
            }
            chunk.setId(chunkId);
        }
    }

    /** 校验 {@code documentId} 和 {@code groupId} 均非空 */
    private void validateIdentifiers(Long documentId, Long groupId) {
        if (documentId == null || groupId == null) {
            throw new BusinessException("切片前必须提供 documentId 和 groupId");
        }
    }

    /** 将 Spring AI {@link Document} 列表转换为 {@link DocumentChunkEntity} 列表，跳过空白文本 */
    private List<DocumentChunkEntity> buildChunkDocuments(Long documentId, Long groupId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new BusinessException(EMPTY_CHUNK_DOCUMENTS_MESSAGE);
        }
        List<DocumentChunkEntity> chunks = new ArrayList<>();
        int fallbackStart = 0;
        for (Document document : documents) {
            String chunkText = normalizeChunkDocumentText(document);
            if (chunkText.isBlank()) {
                continue;
            }
            ChunkRange range = resolveChunkRange(document.getMetadata(), chunkText, fallbackStart);
            chunks.add(buildChunk(documentId, groupId, chunks.size(), chunkText, range.charStart(),
                    range.charEnd(), document.getMetadata()));
            fallbackStart = range.charEnd();
        }
        return chunks;
    }

    /** 统一换行符并去除首尾空白 */
    private String normalizeChunkDocumentText(Document document) {
        if (document == null || document.getText() == null) {
            return "";
        }
        return document.getText()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    /** 从元数据中解析字符范围，校验不可信时回退到自增计算 */
    private ChunkRange resolveChunkRange(Map<String, Object> metadata, String chunkText, int fallbackStart) {
        ChunkRange fallbackRange = fallbackRange(fallbackStart, chunkText.length());
        Integer charStart = readMetadataInt(metadata, "charStart");
        Integer charEnd = readMetadataInt(metadata, "charEnd");
        int chunkLength = chunkText.length();
        if (charStart != null && charEnd != null) {
            return isTrustedRange(charStart, charEnd, chunkLength)
                    ? new ChunkRange(charStart, charEnd)
                    : fallbackRange;
        }
        if (charStart != null) {
            return isTrustedStart(charStart, chunkLength)
                    ? new ChunkRange(charStart, safeAdd(charStart, chunkLength))
                    : fallbackRange;
        }
        if (charEnd != null) {
            return isTrustedEnd(charEnd, chunkLength)
                    ? new ChunkRange(charEnd - chunkLength, charEnd)
                    : fallbackRange;
        }
        return fallbackRange;
    }

    /** 基于上一个切片的结束位置计算回退范围 */
    private ChunkRange fallbackRange(int fallbackStart, int chunkLength) {
        int safeStart = Math.max(0, fallbackStart);
        return new ChunkRange(safeStart, safeAdd(safeStart, chunkLength));
    }

    /** 校验 {@code charStart} 和 {@code charEnd} 与文本长度是否构成可信范围 */
    private boolean isTrustedRange(int charStart, int charEnd, int chunkLength) {
        return isTrustedStart(charStart, chunkLength) && charEnd >= charStart + chunkLength;
    }

    /** 校验 {@code charStart + chunkLength} 不溢出 Integer.MAX_VALUE */
    private boolean isTrustedStart(int charStart, int chunkLength) {
        return (long) charStart + chunkLength <= Integer.MAX_VALUE;
    }

    /** 校验 {@code charEnd} 足以容纳文本长度 */
    private boolean isTrustedEnd(int charEnd, int chunkLength) {
        return charEnd >= chunkLength;
    }

    /** 安全加法，上限为 {@code Integer.MAX_VALUE} */
    private int safeAdd(int value, int delta) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, value) + delta);
    }

    /** 构建单个 {@link DocumentChunkEntity}，填充全部业务字段与时间戳 */
    private DocumentChunkEntity buildChunk(Long documentId, Long groupId, int chunkIndex, String chunkText,
                                           int charStart, int charEnd, Map<String, Object> metadata) {
        LocalDateTime now = LocalDateTime.now();
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setDocumentId(documentId);
        chunk.setGroupId(groupId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(chunkText);
        chunk.setChunkSummary(buildSummary(chunkText));
        chunk.setCharStart(charStart);
        chunk.setCharEnd(charEnd);
        chunk.setMetadataJson(buildMetadataJson(documentId, groupId, chunkIndex, charStart, charEnd, metadata));
        chunk.setCreatedAt(now);
        chunk.setUpdatedAt(now);
        return chunk;
    }

    /** 截取前 {@code CHUNK_SUMMARY_LENGTH} 个字符生成摘要，超长追加 {@code "..."} */
    private String buildSummary(String chunkText) {
        if (chunkText.length() <= CHUNK_SUMMARY_LENGTH) {
            return chunkText;
        }
        return chunkText.substring(0, CHUNK_SUMMARY_LENGTH) + "...";
    }

    /** 序列化元数据 JSON，合并来源元数据并补充系统字段 */
    private String buildMetadataJson(Long documentId, Long groupId, int chunkIndex, int charStart, int charEnd) {
        return buildMetadataJson(documentId, groupId, chunkIndex, charStart, charEnd, Map.of());
    }

    /** 序列化元数据 JSON，合并来源元数据并补充系统字段 */
    private String buildMetadataJson(Long documentId, Long groupId, int chunkIndex, int charStart, int charEnd,
                                     Map<String, Object> sourceMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceMetadata != null && !sourceMetadata.isEmpty()) {
            metadata.putAll(sourceMetadata);
        }
        metadata.put("documentId", documentId);
        metadata.put("groupId", groupId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("charStart", charStart);
        metadata.put("charEnd", charEnd);
        metadata.put("sectionPath", readMetadataString(sourceMetadata, "sectionPath"));
        metadata.put("chunkStrategy", readMetadataString(sourceMetadata, "chunkStrategy", DEFAULT_CHUNK_STRATEGY));
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("文档切片元数据序列化失败", exception);
        }
    }

    /** 从元数据中安全读取整型值，无法解析或越界时返回 {@code null} */
    private Integer readMetadataInt(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number || value instanceof String text && !text.isBlank()) {
            try {
                BigDecimal decimal = new BigDecimal(String.valueOf(value).trim());
                if (decimal.signum() < 0 || decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    return null;
                }
                return decimal.stripTrailingZeros().scale() <= 0 ? decimal.intValueExact() : null;
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 从元数据中安全读取字符串，不存在时返回 {@code null} */
    private String readMetadataString(Map<String, Object> metadata, String key) {
        return readMetadataString(metadata, key, null);
    }

    /** 从元数据中安全读取字符串，不存在时返回默认值 */
    private String readMetadataString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /** 字符位置范围 */
    private record ChunkRange(int charStart, int charEnd) {}
}
