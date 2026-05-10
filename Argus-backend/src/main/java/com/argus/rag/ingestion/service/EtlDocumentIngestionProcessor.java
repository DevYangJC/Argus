package com.argus.rag.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.ingestion.service.pipeline.ChunkService;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import com.argus.rag.ingestion.service.pipeline.parser.DocumentParserFactory;
import com.argus.rag.ingestion.service.pipeline.reader.StoredObjectDocumentReader;
import com.argus.rag.ingestion.service.pipeline.transformer.StructureAwareChunkTransformer;
import com.argus.rag.ingestion.service.pipeline.transformer.TextCleanupTransformer;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import com.argus.rag.engine.storage.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档 ETL 摄入处理器，实现完整的 Extract-Transform-Load 流程。
 *
 * <h3>ETL 流水线</h3>
 * <p>处理过程分为以下阶段，每步均有日志追踪：</p>
 * <ol>
 *     <li><b>查找文档</b> —— 根据 documentId 和 groupId 从数据库查询文档实体</li>
 *     <li><b>文档读取（Extract）</b> —— 通过 {@link StoredObjectDocumentReader}
 *         从 MinIO 下载文件并解析为 Spring AI Document</li>
 *     <li><b>文本清洗（Transform）</b> —— 通过 {@link TextCleanupTransformer}
 *         去除空行、特殊字符等噪音</li>
 *     <li><b>预览文本落库</b> —— 合并清洗后文本的前 {@value #PREVIEW_MAX_LENGTH} 个字符
 *         写入文档表作为预览内容</li>
 *     <li><b>文档切片（Transform）</b> —— 通过 {@link StructureAwareChunkTransformer}
 *         将长文本按结构边界分割为语义完整的切片</li>
 *     <li><b>切片落库（Load）</b> —— 通过 {@link ChunkService} 将切片持久化到数据库</li>
 *     <li><b>向量写入（Load）</b> —— 通过 {@link VectorIngestionService}
 *         将切片写入向量数据库</li>
 * </ol>
 *
 * <h3>异常处理</h3>
 * <p>任何阶段失败都会中断流水线并抛出相应的业务异常，
 * 不会静默吞掉错误，确保问题可追溯。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Slf4j
public class EtlDocumentIngestionProcessor implements DocumentIngestionProcessor {



    /** 文档不存在时的错误消息 */
    private static final String DOCUMENT_NOT_FOUND_MESSAGE = "待入库文档不存在";

    /** 预览文本的最大长度 */
    private static final int PREVIEW_MAX_LENGTH = 200;

    /** 文档数据访问层 */
    private final DocumentMapper documentMapper;

    /** 对象存储服务 */
    private final ObjectStorageService storageService;

    /** 文档解析器工厂 */
    private final DocumentParserFactory parserFactory;

    /** 文本清洗转换器 */
    private final TextCleanupTransformer textCleanupTransformer;

    /** 结构感知切片转换器 */
    private final StructureAwareChunkTransformer chunkTransformer;

    /** 切片存储服务 */
    private final ChunkService chunkService;

    /** 向量写入服务 */
    private final VectorIngestionService vectorService;

    /**
     * 构造 ETL 文档摄入处理器。
     *
     * @param documentMapper          文档数据访问层
     * @param storageService          对象存储服务
     * @param parserFactory           文档解析器工厂
     * @param textCleanupTransformer  文本清洗转换器
     * @param chunkTransformer        结构感知切片转换器
     * @param chunkService            切片存储服务
     * @param vectorService           向量写入服务
     */
    public EtlDocumentIngestionProcessor(
            DocumentMapper documentMapper,
            ObjectStorageService storageService,
            DocumentParserFactory parserFactory,
            TextCleanupTransformer textCleanupTransformer,
            StructureAwareChunkTransformer chunkTransformer,
            ChunkService chunkService,
            VectorIngestionService vectorService
    ) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.parserFactory = parserFactory;
        this.textCleanupTransformer = textCleanupTransformer;
        this.chunkTransformer = chunkTransformer;
        this.chunkService = chunkService;
        this.vectorService = vectorService;
    }

    /**
     * 执行完整的文档 ETL 流水线。
     *
     * <p>按顺序执行：查找文档 → 读取解析 → 文本清洗 → 预览落库 →
     * 切片 → 切片落库 → 向量写入。每步完成后输出 INFO 级别日志。</p>
     *
     * @param documentId 待处理文档的 ID
     * @param groupId    文档所属群组的 ID
     * @throws BusinessException 当文档不存在、预览写入失败或任一步骤发生异常时抛出
     */
    @Override
    public void process(Long documentId, Long groupId) {
        log.info("开始执行文档ETL: documentId={}, groupId={}", documentId, groupId);
        DocumentEntity documentEntity = findDocument(documentId, groupId);
        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);
        List<Document> rawDocuments = reader.get();
        log.info("文档读取完成: documentId={}, groupId={}, rawDocuments={}",
                documentId, groupId, rawDocuments.size());
        List<Document> cleanedDocuments = textCleanupTransformer.apply(rawDocuments);
        log.info("文本清洗完成: documentId={}, groupId={}, cleanedDocuments={}",
                documentId, groupId, cleanedDocuments.size());
        persistPreviewText(documentId, groupId, cleanedDocuments);
        List<Document> chunkDocuments = chunkTransformer.apply(cleanedDocuments);
        log.info("文档切片完成: documentId={}, groupId={}, chunkDocuments={}",
                documentId, groupId, chunkDocuments.size());
        List<DocumentChunkEntity> chunks =
                chunkService.saveChunkDocuments(documentId, groupId, chunkDocuments);
        log.info("切片落库完成: documentId={}, groupId={}, persistedChunks={}",
                documentId, groupId, chunks.size());
        vectorService.ingestChunks(chunks);
        log.info("向量写入完成: documentId={}, groupId={}, vectorChunks={}",
                documentId, groupId, chunks.size());
    }

    /**
     * 根据文档 ID 和群组 ID 查询文档实体。
     *
     * @param documentId 文档 ID
     * @param groupId    群组 ID
     * @return 查询到的文档实体
     * @throws BusinessException 当文档不存在时抛出
     */
    private DocumentEntity findDocument(Long documentId, Long groupId) {
        DocumentEntity documentEntity = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (documentEntity == null) {
            throw new BusinessException(DOCUMENT_NOT_FOUND_MESSAGE);
        }
        return documentEntity;
    }

    /**
     * 将清洗后文档的预览文本写入数据库。
     *
     * <p>合并所有清洗后文档的文本内容，截取前 {@value #PREVIEW_MAX_LENGTH} 个字符作为预览。
     * 更新失败时（影响行数为 0）抛出业务异常。</p>
     *
     * @param documentId       文档 ID
     * @param groupId          群组 ID
     * @param cleanedDocuments 清洗后的 Spring AI Document 列表
     * @throws BusinessException 当预览写入失败（影响行数为 0）时抛出
     */
    private void persistPreviewText(Long documentId, Long groupId, List<Document> cleanedDocuments) {
        String previewText = cleanedDocuments.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .map(String::trim)
                .map(this::truncatePreviewText)
                .orElse(null);
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, documentId)
                .eq(DocumentEntity::getGroupId, groupId)
                .set(DocumentEntity::getPreviewText, previewText)
        );
        if (updated == 0) {
            throw new BusinessException("文档预览写入失败");
        }
    }

    /**
     * 截断文本为预览长度，超出部分直接丢弃。
     *
     * @param previewText 原始预览文本
     * @return 截断后的预览文本，最多 {@value #PREVIEW_MAX_LENGTH} 个字符
     */
    private String truncatePreviewText(String previewText) {
        if (previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }
}
