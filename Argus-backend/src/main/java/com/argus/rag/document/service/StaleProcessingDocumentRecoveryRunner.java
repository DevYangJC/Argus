package com.argus.rag.document.service;

import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.document.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 启动时回收遗留在 PROCESSING 状态的文档。
 *
 * <p>当服务异常中断时，正在处理的文档会长时间保持在 PROCESSING 状态，导致前端永远显示"处理中"。
 * 本组件在 Spring 启动完成后扫描所有 PROCESSING 文档，将其标记为 FAILED，
 * 用户可通过重试功能重新触发处理。
 *
 * <p>通过配置项 {@code document.ingestion.processing-timeout-minutes}（默认 30 分钟）
 * 可以控制将多长时间的 PROCESSING 文档视为"遗留"。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Component
@Slf4j
public class StaleProcessingDocumentRecoveryRunner implements ApplicationRunner {

    /** 默认失败原因：服务中断导致处理未完成 */
    private static final String DEFAULT_FAILURE_REASON = "文档处理任务因服务中断未完成，请重试。";

    /** 文档数据访问 */
    private final DocumentMapper documentMapper;

    /**
     * 构造启动恢复组件。
     *
     * @param documentMapper       文档数据访问层
     * @param staleTimeoutMinutes  遗留超时阈值（分钟），可通过配置项覆盖，默认 30
     */
    public StaleProcessingDocumentRecoveryRunner(
            DocumentMapper documentMapper,
            @Value("${document.ingestion.processing-timeout-minutes:30}") long staleTimeoutMinutes
    ) {
        this.documentMapper = documentMapper;
    }

    /**
     * 应用启动后执行：将遗留的 PROCESSING 文档标记为 FAILED。
     *
     * <p>在事务中执行，确保标记操作的原子性。完成后输出回收数量或"无需回收"日志。
     *
     * @param args 应用启动参数
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        int recovered = documentMapper.markStaleProcessingDocumentsFailed(
                DocumentStatus.PROCESSING.name(),
                DocumentStatus.FAILED.name(),
                DEFAULT_FAILURE_REASON,
                null,
                now
        );
        if (recovered > 0) {
            log.warn(
                    "启动回收遗留处理中文档: recoveredCount={}",
                    recovered
            );
        } else {
            log.info("启动检查遗留处理中文档完成，无需回收");
        }
    }
}
