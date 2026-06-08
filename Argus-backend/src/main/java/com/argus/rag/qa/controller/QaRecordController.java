package com.argus.rag.qa.controller;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.qa.model.vo.QaRecordDetailVO;
import com.argus.rag.qa.model.vo.QaRecordPageVO;
import com.argus.rag.qa.service.QaRecordQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** QA 问答历史查询接口。 */
@OperationLog
@RestController
@RequestMapping("/api/qa/records")
public class QaRecordController {

    private final QaRecordQueryService qaRecordQueryService;

    public QaRecordController(QaRecordQueryService qaRecordQueryService) {
        this.qaRecordQueryService = qaRecordQueryService;
    }

    /** 分页查询当前用户可见的 QA 问答历史。 */
    @GetMapping
    public ApiResponse<QaRecordPageVO> listRecords(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Boolean answered,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(qaRecordQueryService.list(groupId, answered, page, pageSize));
    }

    /** 查询单条 QA 问答记录详情及其引用快照。 */
    @GetMapping("/{recordId}")
    public ApiResponse<QaRecordDetailVO> getRecord(@PathVariable Long recordId) {
        return ApiResponse.success(qaRecordQueryService.getDetail(recordId));
    }

    /** Deletes a QA history record and its persisted citation snapshots. */
    @DeleteMapping("/{recordId}")
    public ApiResponse<Void> deleteRecord(@PathVariable Long recordId) {
        qaRecordQueryService.delete(recordId);
        return ApiResponse.success(null);
    }
}
