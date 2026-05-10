package com.argus.rag.engine.storage;

import com.argus.rag.common.exception.BusinessException;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的对象存储服务实现，使用 MinIO Java SDK 与兼容 S3 协议的对象存储服务交互。
 * <p>
 * 提供文件的上传、下载、服务端合并（compose）和删除功能。
 * <p>
 * <b>注入条件：</b>必须同时配置 {@code storage.minio.endpoint}、
 * {@code storage.minio.access-key} 和 {@code storage.minio.secret-key} 三项，
 * 否则 Spring 会注入 {@link MissingObjectStorageService} 作为替代 Bean。
 * <p>
 * <b>桶管理：</b>上传和合并操作前会自动检测目标桶是否存在，不存在则创建。
 * 使用「双重检查锁定 + ConcurrentHashMap 缓存」保证线程安全和高性能——
 * 已就绪的桶会被缓存，后续请求无需重复检查。
 *
 * @author Argus-RAG Team
 * @see ObjectStorageService         服务契约接口
 * @see MissingObjectStorageService  未配置时的降级占位实现
 */
@Service
@ConditionalOnProperty(prefix = "storage.minio", name = {"endpoint", "access-key", "secret-key"})
@Slf4j
public class MinioStorageService implements ObjectStorageService {


    /** MinIO SDK 中表示「流大小未知」的常量，用于 {@link PutObjectArgs} 的 partSize 参数 */
    private static final long UNKNOWN_STREAM_SIZE = -1L;

    /** MinIO 客户端实例，所有操作均通过该实例完成 */
    private final MinioClient minioClient;

    /** 默认存储桶名称，由配置 {@code storage.minio.bucket} 决定，未配置时默认 {@code argus-rag-documents} */
    private final String bucket;

    /** 桶创建操作的全局互斥锁，确保多线程场景下不会重复建桶 */
    private final Object bucketLock = new Object();

    /** 已确认存在的桶缓存（线程安全），命中时直接跳过建桶流程 */
    private final Set<String> readyBuckets = ConcurrentHashMap.newKeySet();

    /**
     * 通过 Spring 环境变量构造 MinIO 客户端并完成初始化。
     * <p>
     * 会依次读取 {@code storage.minio.endpoint}、{@code storage.minio.access-key}、
     * {@code storage.minio.secret-key} 三个必需配置项，任一缺失则启动失败。
     *
     * @param environment Spring {@link Environment}，用于读取存储配置
     * @throws BusinessException 任一必需配置项缺失或为空时抛出
     */
    public MinioStorageService(Environment environment) {
        String endpoint = requiredProperty(environment, "storage.minio.endpoint");
        String accessKey = requiredProperty(environment, "storage.minio.access-key");
        String secretKey = requiredProperty(environment, "storage.minio.secret-key");
        this.bucket = environment.getProperty("storage.minio.bucket", "argus-rag-documents");
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
        log.info("MinIO 对象存储已初始化: endpoint={}, defaultBucket={}", endpoint, bucket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultBucket() {
        return bucket;
    }

    /**
     * 从 MinIO 读取指定对象的内容。
     * <p>
     * 返回的 {@link InputStream} 由调用方负责关闭，MinIO SDK 不会自动关闭。
     *
     * @param bucket    存储桶名称
     * @param objectKey 对象键
     * @return 对象内容输入流
     * @throws BusinessException 读取失败时抛出（对象不存在、存储桶不存在、网络或认证异常等）
     */
    @Override
    public InputStream getObject(String bucket, String objectKey) {
        try {
            log.debug("读取 MinIO 对象: bucket={}, objectKey={}", bucket, objectKey);
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    "对象存储读取失败: bucket=" + bucket + ", objectKey=" + objectKey, exception);
        }
    }

    /**
     * 上传数据流到 MinIO 的指定桶和路径。
     * <p>
     * 上传前会调用 {@link #ensureBucketExists(String)} 确保目标桶就绪。
     * MinIO SDK 会在上传完成后自动关闭传入的 {@link InputStream}。
     *
     * @param bucket      存储桶名称
     * @param objectKey   对象键 / 目标路径
     * @param inputStream 数据输入流
     * @param objectSize  数据总字节数，未知时传 {@code -1}
     * @param contentType MIME 类型（如 {@code "application/pdf"}、{@code "text/plain"}）
     * @throws BusinessException 上传失败时抛出（桶创建失败、网络异常、认证失败等）
     */
    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream,
                          long objectSize, String contentType) {
        try {
            ensureBucketExists(bucket);
            log.debug("上传 MinIO 对象: bucket={}, objectKey={}, size={}, contentType={}",
                    bucket, objectKey, objectSize, contentType);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, objectSize, UNKNOWN_STREAM_SIZE)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    "对象存储上传失败: bucket=" + bucket + ", objectKey=" + objectKey, exception);
        }
    }

    /**
     * 在 MinIO 服务端将多个已有对象合并为一个新对象（服务端 compose）。
     * <p>
     * 合并操作完全在服务端完成，无需下载源对象再上传，极大减少带宽消耗。
     * <b>典型场景：</b>分片上传完成后，按顺序将各分片合并为完整的原始文件。
     * <p>
     * <b>注意：</b>合并完成后源对象<em>不会</em>自动删除，需调用方手动清理残留分片。
     *
     * @param bucket           存储桶名称
     * @param targetObjectKey  合并后生成的目标对象键
     * @param sourceObjectKeys 源对象键列表（顺序敏感，按列表顺序拼接）
     * @param contentType      目标对象的 MIME 类型
     * @throws BusinessException 合并失败时抛出（源对象不存在、桶不存在、网络异常等）
     */
    @Override
    public void composeObject(String bucket, String targetObjectKey,
                              List<String> sourceObjectKeys, String contentType) {
        if (sourceObjectKeys.isEmpty()) {
            throw new BusinessException("对象存储合并失败: 源对象列表不能为空");
        }
        try {
            ensureBucketExists(bucket);
            List<ComposeSource> sources = sourceObjectKeys.stream()
                    .map(sourceKey -> ComposeSource.builder()
                            .bucket(bucket)
                            .object(sourceKey)
                            .build())
                    .toList();
            log.debug("合并 MinIO 对象: bucket={}, target={}, sourceCount={}",
                    bucket, targetObjectKey, sources.size());
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucket)
                            .object(targetObjectKey)
                            .sources(sources)
                            .extraHeaders(Map.of("Content-Type", contentType))
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    "对象存储合并失败: bucket=" + bucket + ", target=" + targetObjectKey, exception);
        }
    }

    /**
     * 从 MinIO 中删除指定对象。
     * <p>
     * MinIO SDK 对删除不存在的对象做幂等处理（不抛异常），可安全重试。
     *
     * @param bucket    存储桶名称
     * @param objectKey 要删除的对象键
     * @throws BusinessException 删除失败时抛出（权限不足、网络异常等）
     */
    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            log.debug("删除 MinIO 对象: bucket={}, objectKey={}", bucket, objectKey);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    "对象存储删除失败: bucket=" + bucket + ", objectKey=" + objectKey, exception);
        }
    }

    /**
     * 确保目标存储桶在 MinIO 中存在——若不存在则自动创建。
     * <p>
     * 采用高性能的「双重检查锁定 + 缓存」策略保证线程安全和低延迟：
     * <ol>
     *   <li>无锁快速路径：检查 {@link #readyBuckets} 缓存，命中则直接返回</li>
     *   <li>慢速路径：获取 {@link #bucketLock} 全局锁</li>
     *   <li>二次检查缓存（防止竞态窗口期内其他线程已完成建桶）</li>
     *   <li>调用 MinIO API：先 {@code bucketExists} 检查，再 {@code makeBucket} 创建</li>
     *   <li>将桶名加入缓存，后续同桶请求全部走快速路径</li>
     * </ol>
     *
     * @param bucket 存储桶名称
     * @throws Exception MinIO SDK 操作失败时抛出原始异常，由上层方法统一包装
     */
    private void ensureBucketExists(String bucket) throws Exception {
        if (readyBuckets.contains(bucket)) {
            return;
        }
        synchronized (bucketLock) {
            if (readyBuckets.contains(bucket)) {
                return;
            }
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已自动创建 MinIO 存储桶: {}", bucket);
            }
            readyBuckets.add(bucket);
        }
    }

    /**
     * 从 Spring 环境中安全读取必需配置项——缺失或为空时立即抛出明确异常。
     * <p>
     * 该方法在构造阶段调用，确保配置问题在应用启动时就被发现（「早暴露、早修复」）。
     *
     * @param environment  Spring 环境对象
     * @param propertyName 配置项完整路径（如 {@code "storage.minio.endpoint"}）
     * @return 配置项的值（保证非空、非空白）
     * @throws BusinessException 配置项不存在或为空白字符串时抛出，
     *                           提示用户检查 {@code application.yml}
     */
    private String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(
                    "对象存储配置缺失: " + propertyName
                            + "，请在 application.yml / 环境变量中配置 storage.minio.* 相关属性");
        }
        return value;
    }
}
