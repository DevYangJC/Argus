package com.argus.rag.engine.storage;

import com.argus.rag.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储服务的降级占位实现，在 MinIO 未配置时由 Spring 自动注入。
 * <p>
 * 当 {@code storage.minio.endpoint}、{@code storage.minio.access-key}、
 * {@code storage.minio.secret-key} 三项中<em>任意一项</em>为空或未定义时，
 * Spring 会注入此 Bean 作为 {@link ObjectStorageService} 的替代实现，
 * 确保应用在缺少存储配置时仍可正常启动（而非因缺少 Bean 崩溃）。
 * <p>
 * <b>行为特征：</b>所有写/读操作均直接抛出 {@link BusinessException}，
 * 采用「快速失败」（fail-fast）策略——不返回空数据或假成功，
 * 而是在运行时明确告知调用方存储不可用，迫使用户完成配置。
 * <p>
 * <b>注入条件：</b>当 Spring 容器中不存在任何 {@link ObjectStorageService} 实现时自动注入。
 *
 * @author Argus-RAG Team
 * @see ObjectStorageService  服务契约接口
 * @see MinioStorageService   正常配置时的生产级 MinIO 实现
 */
@Service
@ConditionalOnMissingBean(ObjectStorageService.class)
@Slf4j
public class MissingObjectStorageService implements ObjectStorageService {


    /** 统一的「对象存储未配置」错误提示，所有方法共享 */
    private static final String NOT_CONFIGURED_MESSAGE =
            "对象存储未配置，请在 application.yml 或环境变量中设置 "
                    + "storage.minio.endpoint、storage.minio.access-key 和 storage.minio.secret-key";

    /** 默认存储桶名称，从配置中读取，未配置时默认为 {@code argus-rag-documents} */
    private final String bucket;

    /**
     * 构造降级服务实例。
     * <p>
     * 在构造时记录 WARN 级别日志，提醒运维人员当前存储功能不可用。
     *
     * @param environment Spring 环境对象
     */
    public MissingObjectStorageService(Environment environment) {
        this.bucket = environment.getProperty("storage.minio.bucket", "argus-rag-documents");
        log.warn("对象存储未配置！已启用 MissingObjectStorageService 降级实现，所有存储操作将抛出 BusinessException。"
                + "请配置 storage.minio.endpoint / access-key / secret-key 以启用 MinIO 存储。");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultBucket() {
        return bucket;
    }

    /**
     * 读取对象 —— 当前不可用。
     *
     * @throws BusinessException 始终抛出，错误信息见 {@link #NOT_CONFIGURED_MESSAGE}
     */
    @Override
    public InputStream getObject(String bucket, String objectKey) {
        throw new BusinessException(NOT_CONFIGURED_MESSAGE);
    }

    /**
     * 上传对象 —— 当前不可用。
     *
     * @throws BusinessException 始终抛出，错误信息见 {@link #NOT_CONFIGURED_MESSAGE}
     */
    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream,
                          long objectSize, String contentType) {
        throw new BusinessException(NOT_CONFIGURED_MESSAGE);
    }

    /**
     * 合并对象 —— 当前不可用。
     *
     * @throws BusinessException 始终抛出，错误信息见 {@link #NOT_CONFIGURED_MESSAGE}
     */
    @Override
    public void composeObject(String bucket, String targetObjectKey,
                              List<String> sourceObjectKeys, String contentType) {
        throw new BusinessException(NOT_CONFIGURED_MESSAGE);
    }

    /**
     * 删除对象 —— 当前不可用。
     *
     * @throws BusinessException 始终抛出，错误信息见 {@link #NOT_CONFIGURED_MESSAGE}
     */
    @Override
    public void deleteObject(String bucket, String objectKey) {
        throw new BusinessException(NOT_CONFIGURED_MESSAGE);
    }
}
