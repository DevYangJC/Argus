package com.argus.rag.qa.rag;

import com.argus.rag.qa.model.EvidenceLevel;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 检索证据束，封装混合检索的结果。
 * <p>
 * 包含检索到的文档列表、证据充分度等级和对应的回答指导语。
 * </p>
 *
 * @param documents       检索到的文档列表，每个文档包含证据文本和元数据
 * @param evidenceLevel   证据充分度等级，取值见 {@link EvidenceLevel}
 * @param evidenceGuidance 证据指导语，告知大模型当前证据状态下的回答策略
 */
public record RetrievedEvidenceBundle(
        List<Document> documents,
        EvidenceLevel evidenceLevel,
        String evidenceGuidance
) {

    /**
     * 创建空的证据束，表示未检索到任何证据。
     *
     * @return 空证据束，证据等级为 NONE
     */
    public static RetrievedEvidenceBundle empty() {
        return new RetrievedEvidenceBundle(
                List.of(),
                EvidenceLevel.NONE,
                "当前没有可用证据，必须直接拒答。"
        );
    }
}
