package com.argus.rag.qa.support;

import com.argus.rag.qa.model.vo.AskQuestionResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引用来源组装器。
 * <p>
 * 将检索返回的 {@link Document} 列表转换为去重后的 {@link AskQuestionResponse.Citation} 列表。
 * 按文件名去重，保留每个文件的第一次命中结果。
 * </p>
 */
@Component
public class CitationAssembler {

    /**
     * 将检索文档列表组装为引用来源列表。
     * <p>按文件名去重，保留每个文件的第一次命中，保持插入顺序。</p>
     *
     * @param documents 检索返回的文档列表
     * @return 去重后的引用来源列表
     */
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

    /**
     * 将单个检索文档转换为引用来源对象。
     * <p>从文档元数据中提取各字段，若缺少文件名则返回 {@code null}。</p>
     */
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

    /** 从元数据中安全读取 Long 类型值，非 Number 类型时返回 {@code null} */
    private Long readLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /** 从元数据中安全读取 Integer 类型值，非 Number 类型时返回 {@code null} */
    private Integer readInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /** 从元数据中读取检索评分，默认返回 0 */
    private double readScore(Map<String, Object> metadata) {
        Object value = metadata.get("score");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    /** 从元数据中安全读取 String 类型值，自动 trim，非 String 类型时返回 {@code null} */
    private String readText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String text ? text.trim() : null;
    }

    /** 从元数据中读取文件名，优先读取 "fileName"，回退读取 "documentName" */
    private String readFileName(Map<String, Object> metadata) {
        String fileName = readText(metadata, "fileName");
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        return readText(metadata, "documentName");
    }
}
