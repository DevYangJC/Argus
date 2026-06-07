package com.argus.rag.qa.model.vo;

import java.time.LocalDateTime;
import java.util.List;

/** QA 历史详情响应，包含完整问答、用量和引用快照。 */
public record QaRecordDetailVO(
        /** 问答记录 ID。 */
        Long id,
        /** 提问用户 ID。 */
        Long userId,
        /** 所属群组 ID。 */
        Long groupId,
        /** 用户原始问题。 */
        String question,
        /** 回答正文，拒答或失败时可为空。 */
        String answer,
        /** 是否成功回答。 */
        Boolean answered,
        /** 拒答或失败原因编码。 */
        String reasonCode,
        /** 拒答或失败原因说明。 */
        String reasonMessage,
        /** 证据充分性等级。 */
        String evidenceLevel,
        /** 引用快照数量。 */
        Integer citationCount,
        /** 输入 token 数。 */
        Integer promptTokens,
        /** 输出 token 数。 */
        Integer completionTokens,
        /** 总 token 数。 */
        Integer totalTokens,
        /** token 用量是否为估算值。 */
        Boolean isEstimated,
        /** 问答耗时，单位毫秒。 */
        Long latencyMs,
        /** 调用的大模型名称。 */
        String modelName,
        /** 触发记录的接口端点。 */
        String endpoint,
        /** 本次问答流程是否成功完成。 */
        Boolean success,
        /** 系统异常信息。 */
        String errorMessage,
        /** 记录创建时间。 */
        LocalDateTime createdAt,
        /** Persisted citations assembled into the evidence overview panel shape. */
        AskQuestionResponse.EvidenceOverview evidenceOverview,
        /** 回答时保存的引用快照。 */
        List<Citation> citations
) {
    /** 返回一份替换引用快照后的详情对象。 */
    public QaRecordDetailVO withCitations(List<Citation> citations) {
        return new QaRecordDetailVO(
                id,
                userId,
                groupId,
                question,
                answer,
                answered,
                reasonCode,
                reasonMessage,
                evidenceLevel,
                citationCount,
                promptTokens,
                completionTokens,
                totalTokens,
                isEstimated,
                latencyMs,
                modelName,
                endpoint,
                success,
                errorMessage,
                createdAt,
                evidenceOverview,
                citations
        );
    }

    /** QA 回答使用的单条引用快照。 */
    public record Citation(
            /** 来源文档 ID。 */
            Long documentId,
            /** 来源文档版本 ID。 */
            Long documentVersionId,
            /** 来源切片 ID。 */
            Long chunkId,
            /** 切片在文档中的序号。 */
            Integer chunkIndex,
            /** 合并证据的起始切片序号。 */
            Integer startChunkIndex,
            /** 合并证据的结束切片序号。 */
            Integer endChunkIndex,
            /** 来源文件名。 */
            String fileName,
            /** 最终相关性得分。 */
            Double score,
            /** 检索来源类型。 */
            String retrievalSource,
            /** 向量检索得分。 */
            Double vectorScore,
            /** 关键词检索得分。 */
            Double keywordScore,
            /** 混合检索融合得分。 */
            Double hybridScore,
            /** 回答时使用的证据文本快照。 */
            String snippet
    ) {
    }
}
