package com.argus.rag.document.model.vo;

import java.util.List;

/**
 * 上传状态查询响应 VO。
 * <p>
 * 返回上传会话的当前状态和进度信息，前端可以轮询此接口获取上传进度。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public record UploadStatusResponse(
        /** 上传会话当前状态（UPLOADING/MERGING/UPLOADED/FAILED） */
        String status,
        /** 已成功上传的分片序号列表 */
        List<Integer> uploadedChunks,
        /** 已上传的分片数量 */
        Integer uploadedChunkCount,
        /** 总分片数量，用于计算上传进度百分比 */
        Integer chunkCount
) {
}
