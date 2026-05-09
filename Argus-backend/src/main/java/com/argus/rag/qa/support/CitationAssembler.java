package com.dong.ddrag.qa.support;

import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CitationAssembler {

    public List<AskQuestionResponse.Citation> assembleDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Map<String, AskQuestionResponse.Citation> citationsByFileName = new LinkedHashMap<>();
        for (Document document : documents) {
            AskQuestionResponse.Citation citation = toCitation(document);
            if (citation != null) {
                citationsByFileName.putIfAbsent(citation.fileName(), citation);
            }
        }
        return List.copyOf(citationsByFileName.values());
    }

    private AskQuestionResponse.Citation toCitation(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String fileName = readFileName(metadata);
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        return new AskQuestionResponse.Citation(
                readLong(metadata, "documentId"),
                readLong(metadata, "chunkId"),
                readInteger(metadata, "chunkIndex"),
                fileName,
                readScore(metadata),
                null
        );
    }

    private Long readLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private double readScore(Map<String, Object> metadata) {
        Object value = metadata.get("score");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    private String readText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String text ? text.trim() : null;
    }

    private String readFileName(Map<String, Object> metadata) {
        String fileName = readText(metadata, "fileName");
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        return readText(metadata, "documentName");
    }
}
