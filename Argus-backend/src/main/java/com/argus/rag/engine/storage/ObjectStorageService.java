package com.argus.rag.engine.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储服务契约接口，定义了统一的文件级 CRUD 操作。
 * <p>
 * 调用方只需依赖此接口，无需关心底层存储实现（MinIO、S3、本地文件系统等）。
 * 接口设计遵循「存储桶（Bucket）+ 对象键（ObjectKey）」的扁平命名模型，
 * 适配绝大多数对象存储产品。
 *
 * @author Argus-RAG Team
 * @see MinioStorageService         基于 MinIO 的生产级实现
 * @see MissingObjectStorageService 未配置存储时的降级占位实现
 */
public interface ObjectStorageService {

    /**
     * 获取默认存储桶名称。
     * <p>
     * 桶名由配置决定（如 {@code storage.minio.bucket}），未配置时回退为 {@code "argus-rag-documents"}。
     *
     * @return 默认桶名，不会为 {@code null}
     */
    String getDefaultBucket();

    /**
     * 从指定存储桶中读取对象，返回其内容输入流。
     * <p>
     * 调用方负责在使用完毕后关闭返回的 {@link InputStream}。
     *
     * @param bucket    存储桶名称（不能为空）
     * @param objectKey 对象键 / 文件路径（不能为空）
     * @return 对象内容的输入流，可供下游按需消费
     * @throws com.argus.rag.common.exception.BusinessException 读取失败时抛出（如对象不存在、权限不足、网络异常）
     */
    InputStream getObject(String bucket, String objectKey);

    /**
     * 将输入流中的数据上传为对象存储中的一个对象。
     * <p>
     * 如果目标桶不存在，实现层应负责自动创建（幂等保证）。
     *
     * @param bucket      存储桶名称（不能为空）
     * @param objectKey   对象键 / 目标路径（不能为空）
     * @param inputStream 要上传的数据流（由实现方负责关闭）
     * @param objectSize  数据总字节数，若未知可传 {@code -1}
     * @param contentType MIME 类型（如 {@code "application/pdf"}）
     * @throws com.argus.rag.common.exception.BusinessException 上传失败时抛出
     */
    void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType);

    /**
     * 将多个已存在的对象合并为一个新对象（服务端合并，无需下载再上传）。
     * <p>
     * 典型用法：分片上传完成后，将各分片按顺序合并为完整文件。
     * 合并完成后，源对象通常需要由调用方自行清理。
     *
     * @param bucket           存储桶名称（不能为空）
     * @param targetObjectKey  合并后生成的目标对象键（不能为空）
     * @param sourceObjectKeys 源对象键列表（顺序敏感，按列表顺序拼接）
     * @param contentType      目标对象的 MIME 类型
     * @throws com.argus.rag.common.exception.BusinessException 合并失败时抛出
     */
    void composeObject(String bucket, String targetObjectKey, List<String> sourceObjectKeys, String contentType);

    /**
     * 从指定存储桶中删除一个对象。
     * <p>
     * 删除不存在的对象通常不会报错（幂等操作）。
     *
     * @param bucket    存储桶名称（不能为空）
     * @param objectKey 要删除的对象键（不能为空）
     * @throws com.argus.rag.common.exception.BusinessException 删除失败时抛出（如权限不足、网络异常）
     */
    void deleteObject(String bucket, String objectKey);
}
