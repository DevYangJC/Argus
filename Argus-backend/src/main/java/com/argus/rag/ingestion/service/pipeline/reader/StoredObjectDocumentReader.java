package com.argus.rag.ingestion.service.pipeline.reader;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.ingestion.service.pipeline.parser.DocumentParserFactory;
import com.argus.rag.ingestion.service.pipeline.parser.DocumentParser;
import com.argus.rag.engine.storage.ObjectStorageService;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从对象存储读取文档的读取器，实现 Spring AI 的 {@link DocumentReader} 接口。
 *
 * <p>完整执行流程如下：
 * <ol>
 *     <li>校验 {@link DocumentEntity} 中的 documentId 和 groupId 是否存在</li>
 *     <li>解析存储桶：优先使用文档实体上指定的桶，否则回退到默认桶</li>
 *     <li>根据文件扩展名从 {@link DocumentParserFactory} 获取对应的解析器</li>
 *     <li>通过 {@link ObjectStorageService} 获取文件输入流</li>
 *     <li>调用解析器将文件内容转为纯文本</li>
 *     <li>构建 {@link Document} 对象，附带元数据（groupId、documentId、fileName、source）</li>
 * </ol>
 *
 * <p>source 字段使用 {@code minio://bucket/objectKey} 格式标识文档来源。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public class StoredObjectDocumentReader implements DocumentReader {

    /** MinIO 来源前缀，用于构建文档的 source 元数据 */
    private static final String MINIO_SOURCE_PREFIX = "minio://";

    /** 对象存储服务，用于从 MinIO 下载文件 */
    private final ObjectStorageService storageService;

    /** 解析器工厂，根据文件扩展名获取对应的文档解析器 */
    private final DocumentParserFactory parserFactory;

    /** 待读取的文档实体，包含存储桶、对象键、文件扩展名等元信息 */
    private final DocumentEntity documentEntity;

    /**
     * 构造一个对象存储文档读取器。
     *
     * @param storageService 对象存储服务，用于获取文件输入流
     * @param parserFactory  文档解析器工厂，用于获取与文件类型匹配的解析器
     * @param documentEntity 待读取的文档实体，包含存储位置和文件信息
     */
    public StoredObjectDocumentReader(
            ObjectStorageService storageService,
            DocumentParserFactory parserFactory,
            DocumentEntity documentEntity
    ) {
        this.storageService = storageService;
        this.parserFactory = parserFactory;
        this.documentEntity = documentEntity;
    }

    /**
     * 执行文档读取操作：从对象存储下载文件，解析为文本，并构建 Spring AI Document。
     *
     * @return 包含解析后文本内容和元数据的 {@link Document} 列表
     * @throws BusinessException 当文档实体校验失败、文件扩展名不支持、或读取/解析过程中发生异常时抛出
     */
    @Override
    public List<Document> get() {
        validateDocumentEntity();
        String bucket = resolveBucket();
        String objectKey = documentEntity.getStorageObjectKey();
        DocumentParser parser = parserFactory.getParser(documentEntity.getFileExt());
        try (InputStream inputStream = storageService.getObject(bucket, objectKey)) {
            String content = parser.parse(inputStream);
            return List.of(buildDocument(content, bucket, objectKey));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("读取存储文档失败", exception);
        }
    }

    /**
     * 校验文档实体是否包含必要的 documentId 和 groupId。
     *
     * @throws BusinessException 当实体为 null 或缺少 ID/groupId 时抛出
     */
    private void validateDocumentEntity() {
        if (documentEntity == null || documentEntity.getId() == null || documentEntity.getGroupId() == null) {
            throw new BusinessException("读取文档前必须提供 documentId 和 groupId");
        }
    }

    /**
     * 解析存储桶名称。
     *
     * <p>优先使用文档实体上显式指定的桶，若为空则回退到对象存储服务的默认桶。
     *
     * @return 最终使用的存储桶名称
     */
    private String resolveBucket() {
        if (StringUtils.hasText(documentEntity.getStorageBucket())) {
            return documentEntity.getStorageBucket();
        }
        return storageService.getDefaultBucket();
    }

    /**
     * 根据解析后的文本内容构建 Spring AI Document。
     *
     * @param content   解析后的纯文本内容
     * @param bucket    存储桶名称
     * @param objectKey 对象存储键
     * @return 附带文本内容和元数据的 {@link Document}
     */
    private Document buildDocument(String content, String bucket, String objectKey) {
        return Document.builder()
                .id(String.valueOf(documentEntity.getId()))
                .text(content)
                .metadata(buildMetadata(bucket, objectKey))
                .build();
    }

    /**
     * 构建文档的元数据映射。
     *
     * @param bucket    存储桶名称
     * @param objectKey 对象存储键
     * @return 包含 groupId、documentId、fileName、source 的元数据 Map
     */
    private Map<String, Object> buildMetadata(String bucket, String objectKey) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("groupId", documentEntity.getGroupId());
        metadata.put("documentId", documentEntity.getId());
        metadata.put("fileName", documentEntity.getFileName());
        metadata.put("source", MINIO_SOURCE_PREFIX + bucket + "/" + objectKey);
        return metadata;
    }
}
