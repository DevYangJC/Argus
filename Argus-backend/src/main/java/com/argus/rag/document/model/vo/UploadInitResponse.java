package com.argus.rag.document.model.vo;

import java.util.List;

/**
 * 上传初始化响应 VO。
 * <p>
 * 根据不同的初始化结果返回不同数据：
 * <ul>
 *   <li>秒传：相同文件已存在，直接返回已处理的 documentId，无需上传</li>
 *   <li>断点续传：已有上传会话，返回 uploadId 和已上传的分片序号列表</li>
 *   <li>新建上传：创建新的上传会话，返回 uploadId 和分片参数</li>
 * </ul>
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public record UploadInitResponse(
        /** 是否为秒传，true 表示无需上传，前端直接跳转到文档详情页 */
        boolean instantUpload,
        /** 秒传成功时已有的文档 ID，仅 instantUpload=true 时有值 */
        Long documentId,
        /** 上传会话唯一标识（UUID），用于后续分片上传和状态查询 */
        String uploadId,
        /** 已上传的分片序号列表，用于断点续传时告知前端跳过这些分片 */
        List<Integer> uploadedChunks,
        /** 每个分片的大小（字节） */
        Long chunkSize,
        /** 总分片数量 */
        Integer chunkCount
) {

    /**
     * 创建秒传成功响应。
     *
     * @param documentId 已存在的文档 ID
     * @return 秒传响应
     */
    public static UploadInitResponse instant(Long documentId) {
        return new UploadInitResponse(true, documentId, null, List.of(), null, null);
    }

    /**
     * 创建新建上传会话响应（无已上传分片）。
     *
     * @param uploadId   上传会话标识
     * @param chunkSize  分片大小（字节）
     * @param chunkCount 总分片数
     * @return 新建上传响应
     */
    public static UploadInitResponse uploadSession(String uploadId, Long chunkSize, Integer chunkCount) {
        return new UploadInitResponse(false, null, uploadId, List.of(), chunkSize, chunkCount);
    }

    /**
     * 创建断点续传响应（包含已上传分片列表）。
     *
     * @param uploadId        上传会话标识
     * @param uploadedChunks  已成功上传的分片序号列表
     * @param chunkSize       分片大小（字节）
     * @param chunkCount      总分片数
     * @return 断点续传响应
     */
    public static UploadInitResponse uploadSession(
            String uploadId,
            List<Integer> uploadedChunks,
            Long chunkSize,
            Integer chunkCount
    ) {
        return new UploadInitResponse(false, null, uploadId, uploadedChunks, chunkSize, chunkCount);
    }
}
