package com.argus.rag.document.service;

import com.argus.rag.ingestion.service.DocumentIngestionAsyncService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档摄入事件异步监听器。
 *
 * <p>监听 {@link DocumentIngestionRequestedEvent} 事件，在事务提交后异步执行文档 ETL。
 * 使用 {@link Async} 确保 ETL 不阻塞上传请求的响应返回，
 * 使用 {@link TransactionalEventListener} 的 AFTER_COMMIT 阶段确保只有在
 * 数据库事务成功提交后才触发 ETL。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Component
@Slf4j
public class DocumentIngestionAsyncListener {

    /** 异步 ETL 执行服务 */
    private final DocumentIngestionAsyncService documentIngestionAsyncService;

    /**
     * 构造异步监听器，注入 ETL 服务。
     *
     * @param documentIngestionAsyncService 异步 ETL 服务
     */
    public DocumentIngestionAsyncListener(DocumentIngestionAsyncService documentIngestionAsyncService) {
        this.documentIngestionAsyncService = documentIngestionAsyncService;
    }

    /**
     * 处理文档摄入请求事件。
     *
     * <p>在原始事务提交后异步执行，确保此时文档记录已在数据库可见。
     * 若 ETL 失败，由 {@link DocumentIngestionAsyncService#recover} 进行兜底。
     *
     * @param event 文档摄入请求事件（包含 documentId 和 groupId）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentIngestionRequestedEvent event) {
        log.info("收到文档异步ETL事件(事务已提交): documentId={}, groupId={}", event.documentId(), event.groupId());
        documentIngestionAsyncService.ingestDocument(event.documentId(), event.groupId());
    }
}
