package com.argus.rag.ingestion.service.pipeline.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 清洗原始文本中的噪声（控制字符、多余空白行等），保留代码块格式不变。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public class TextCleanupTransformer implements DocumentTransformer {

    /** 代码块分隔符允许的最大缩进 */
    private static final int MAX_FENCE_INDENT = 3;
    /** 代码块分隔符的最小连续字符数 */
    private static final int MIN_FENCE_LENGTH = 3;
    /** 匹配控制字符的正则 */
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    /** 匹配行内连续空格与制表符的正则 */
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[ \\t]+");
    /** 匹配连续三个及以上换行的正则，用于压缩多余空行 */
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile("\\n{3,}");

    /**
     * @param documents 原始文档列表
     * @return 清洗后的文档列表
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream()
                .map(this::cleanupDocument)
                .toList();
    }

    // 清洗单个文档的文本内容
    private Document cleanupDocument(Document document) {
        if (document.getText() == null) {
            return document;
        }
        return document.mutate()
                .text(clean(document.getText()))
                .build();
    }

    /**
     * @param source 原始文本
     * @return 清洗后的文本
     */
    String clean(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        normalized = CONTROL_CHARACTERS.matcher(normalized).replaceAll("");
        return cleanLines(normalized);
    }

    // 压缩行内空白并去除首尾空白
    private String normalizeLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        return INLINE_WHITESPACE.matcher(trimmed).replaceAll(" ");
    }

    // 按行处理文本，代码块内容原样保留，普通文本段压缩多余空行
    private String cleanLines(String normalized) {
        List<String> segments = new ArrayList<>();
        List<String> plainLines = new ArrayList<>();
        List<String> fenceLines = null;
        FenceMarker openingFence = null;

        for (String line : normalized.split("\n", -1)) {
            if (openingFence != null) {
                if (isClosingFence(line, openingFence)) {
                    fenceLines.add(line);
                    segments.add(String.join("\n", fenceLines));
                    fenceLines = null;
                    openingFence = null;
                } else {
                    fenceLines.add(line);
                }
                continue;
            }

            FenceMarker candidateFence = parseOpeningFence(line);
            if (candidateFence != null) {
                appendPlainSegment(segments, plainLines);
                fenceLines = new ArrayList<>();
                fenceLines.add(line);
                openingFence = candidateFence;
                continue;
            }

            plainLines.add(line);
        }

        if (openingFence != null && fenceLines != null) {
            segments.add(String.join("\n", fenceLines));
        }
        appendPlainSegment(segments, plainLines);
        return String.join("\n", segments);
    }

    // 解析代码块起始分隔符（三个及以上连续的 ` 或 ~）
    private FenceMarker parseOpeningFence(String line) {
        int contentStart = countLeadingSpaces(line);
        if (contentStart < 0 || contentStart >= line.length()) {
            return null;
        }

        char marker = line.charAt(contentStart);
        int fenceLength = countFenceLength(line, contentStart, marker);
        if ((marker != '`' && marker != '~') || fenceLength < MIN_FENCE_LENGTH) {
            return null;
        }
        return new FenceMarker(marker, fenceLength);
    }

    // 判断是否为对应的代码块结束分隔符
    private boolean isClosingFence(String line, FenceMarker openingFence) {
        int contentStart = countLeadingSpaces(line);
        if (contentStart < 0 || contentStart >= line.length()) {
            return false;
        }

        int fenceLength = countFenceLength(line, contentStart, openingFence.marker());
        return line.charAt(contentStart) == openingFence.marker()
                && fenceLength >= openingFence.length()
                && hasOnlyTrailingWhitespace(line, contentStart + fenceLength);
    }

    // 统计行首空格数，超过 {@link #MAX_FENCE_INDENT} 返回 -1
    private int countLeadingSpaces(String line) {
        int leadingSpaces = 0;
        while (leadingSpaces < line.length() && line.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        return leadingSpaces <= MAX_FENCE_INDENT ? leadingSpaces : -1;
    }

    // 从指定位置统计连续相同字符的个数
    private int countFenceLength(String line, int startIndex, char marker) {
        int fenceLength = 0;
        while (startIndex + fenceLength < line.length() && line.charAt(startIndex + fenceLength) == marker) {
            fenceLength++;
        }
        return fenceLength;
    }

    // 检查从指定位置起到行尾是否仅有空白字符
    private boolean hasOnlyTrailingWhitespace(String line, int startIndex) {
        for (int index = startIndex; index < line.length(); index++) {
            if (!Character.isWhitespace(line.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    // 将缓存的普通文本行归一化后追加到结果段列表
    private void appendPlainSegment(List<String> segments, List<String> plainLines) {
        if (plainLines.isEmpty()) {
            return;
        }

        String normalizedPlainText = plainLines.stream()
                .map(this::normalizeLine)
                .collect(Collectors.joining("\n"));
        segments.add(EXCESSIVE_BLANK_LINES.matcher(normalizedPlainText).replaceAll("\n\n"));
        plainLines.clear();
    }

    /** 代码块分隔符标记 */
    private record FenceMarker(char marker, int length) {
    }
}
