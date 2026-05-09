package com.dong.ddrag.qa.rag;

import com.dong.ddrag.qa.model.EvidenceLevel;
import org.springframework.ai.document.Document;

import java.util.List;

public record RetrievedEvidenceBundle(
        List<Document> documents,
        EvidenceLevel evidenceLevel,
        String evidenceGuidance
) {

    public static RetrievedEvidenceBundle empty() {
        return new RetrievedEvidenceBundle(
                List.of(),
                EvidenceLevel.NONE,
                "当前没有可用证据，必须直接拒答。"
        );
    }
}
