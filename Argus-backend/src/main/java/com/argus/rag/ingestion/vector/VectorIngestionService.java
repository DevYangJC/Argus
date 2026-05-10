package com.argus.rag.ingestion.vector;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 向量写入服务，负责将 {@link DocumentChunkEntity} 批量写入 {@link VectorStore} 并管理元数据。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class VectorIngestionService {

    /** 默认的向量写入批次大小 */
    private static final int DEFAULT_ADD_BATCH_SIZE = 9;

    /** Jackson 反序列化元数据 JSON 时复用的 {@link TypeReference} */
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };

    private final VectorStore vectorStore;

    /** 配置注入的向量写入批次大小，已通过 {@link #normalizeBatchSize} 校验 */
    private final int addBatchSize;

    /** 用于解析 chunk 的 metadataJson 字段 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生产环境构造器，通过 Spring 注入 {@link VectorStore} 和批次大小配置。
     *
     * @param vectorStore  Spring AI 向量存储接口
     * @param addBatchSize 来源于 {@code ingestion.vector.add-batch-size} 配置
     */
    @Autowired
    public VectorIngestionService(
            VectorStore vectorStore,
            @Value("${ingestion.vector.add-batch-size:${spring.ai.vectorstore.pgvector.max-document-batch-size:9}}")
            int addBatchSize
    ) {
        this.vectorStore = vectorStore;
        this.addBatchSize = normalizeBatchSize(addBatchSize);
    }

    /**
     * 手工构造入口，用于单测等场景，批次大小默认为 {@value #DEFAULT_ADD_BATCH_SIZE}。
     *
     * @param vectorStore Spring AI 向量存储接口
     */
    public VectorIngestionService(VectorStore vectorStore) {
        this(vectorStore, DEFAULT_ADD_BATCH_SIZE);
    }

    /**
     * 将一批 {@link DocumentChunkEntity} 写入向量库：先删除已有同源向量，再分批写入新向量。
     *
     * @param chunks 待写入的 chunk 列表，为 {@code null} 或空列表时直接返回
     * @throws BusinessException 当 chunk 未落库或缺少必要字段时抛出
     */
    public void ingestChunks(List<DocumentChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        log.info("开始写入向量: chunkCount={}, addBatchSize={}", chunks.size(), addBatchSize);
        List<Document> documents = chunks.stream().map(this::toVectorDocument).toList();
        deleteExistingVectors(extractDocumentIds(chunks));
        embedAndStore(documents);
        log.info("向量写入结束: chunkCount={}", documents.size());
    }

    /**
     * 删除指定文档在向量库中的所有向量记录。
     *
     * @param documentId 文档 ID，为 {@code null} 或小于等于 0 时直接返回
     */
    public void deleteDocumentVectors(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        Filter.Expression filter = new FilterExpressionBuilder().eq("documentId", documentId).build();
        vectorStore.delete(filter);
        log.info("文档向量删除完成: documentId={}", documentId);
    }

    /**
     * 按 {@link #addBatchSize} 分批写入向量文档到 {@link VectorStore}。
     *
     * @param documents 待写入的 Spring AI {@link Document} 列表，为空时直接返回
     */
    public void embedAndStore(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        for (int i = 0; i < documents.size(); i += addBatchSize) {
            List<Document> subList = documents.subList(i, Math.min(i + addBatchSize, documents.size()));
            log.info("执行向量批次写入: batchStart={}, batchSize={}", i, subList.size());
            vectorStore.add(subList);
        }
    }

    // 将配置值 normalize 为有效正整数，非正数退回默认值。
    private int normalizeBatchSize(int configuredBatchSize) {
        return configuredBatchSize > 0 ? configuredBatchSize : DEFAULT_ADD_BATCH_SIZE;
    }

    // 将 DocumentChunkEntity 转换为 Spring AI Document，包含稳定的向量 ID 及元数据。
    private Document toVectorDocument(DocumentChunkEntity chunk) {
        validateChunk(chunk);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("groupId", chunk.getGroupId());
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkId", chunk.getId());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.putAll(extractOptionalMetadata(chunk.getMetadataJson()));
        return Document.builder()
                .id(buildStableDocumentId(chunk))
                .text(chunk.getChunkText())
                .metadata(metadata)
                .build();
    }

    // 按文档维度删除向量库中已有记录，确保幂等写入。
    private void deleteExistingVectors(Set<Long> documentIds) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        for (Long documentId : documentIds) {
            Filter.Expression filter = builder.eq("documentId", documentId).build();
            vectorStore.delete(filter);
        }
    }

    // 从 chunk 列表中提取去重后的 documentId 集合。
    private Set<Long> extractDocumentIds(List<DocumentChunkEntity> chunks) {
        Set<Long> documentIds = new LinkedHashSet<>();
        for (DocumentChunkEntity chunk : chunks) {
            validateChunk(chunk);
            documentIds.add(chunk.getDocumentId());
        }
        return documentIds;
    }

    // 基于 documentId 和 chunkIndex 生成稳定的 UUID 作为向量记录唯一标识。
    private String buildStableDocumentId(DocumentChunkEntity chunk) {
        String rawId = chunk.getDocumentId() + ":" + chunk.getChunkIndex();
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    // 校验 chunk 必需字段，不合法时直接抛出 BusinessException。
    private void validateChunk(DocumentChunkEntity chunk) {
        if (chunk == null || chunk.getId() == null) {
            throw new BusinessException("向量写入前必须先完成 chunk 落库");
        }
        if (chunk.getDocumentId() == null || chunk.getChunkIndex() == null) {
            throw new BusinessException("向量写入前必须提供 documentId 和 chunkIndex");
        }
        if (chunk.getChunkText() == null || chunk.getChunkText().isBlank()) {
            throw new BusinessException("空切片不能写入向量库");
        }
    }

    // 从 metadataJson 中提取可选的扩展元数据，解析失败时返回空 Map 并记录 warn 日志。
    private Map<String, Object> extractOptionalMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> sourceMetadata = objectMapper.readValue(metadataJson, MAP_TYPE_REFERENCE);
            Map<String, Object> optionalMetadata = new LinkedHashMap<>();
            String fileName = readLegacyCompatibleFileName(sourceMetadata);
            if (fileName != null) {
                optionalMetadata.put("fileName", fileName);
            }
            return optionalMetadata;
        } catch (Exception exception) {
            log.warn("解析 chunk metadataJson 失败，忽略扩展元数据。", exception);
            return Map.of();
        }
    }

    // 兼容读取 fileName 或 documentName 字段，优先取 fileName。
    private String readLegacyCompatibleFileName(Map<String, Object> sourceMetadata) {
        Object fileName = sourceMetadata.get("fileName");
        if (fileName instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        Object documentName = sourceMetadata.get("documentName");
        if (documentName instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }
}
