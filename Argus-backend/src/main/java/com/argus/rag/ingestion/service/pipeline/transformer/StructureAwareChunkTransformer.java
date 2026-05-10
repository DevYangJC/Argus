package com.argus.rag.ingestion.service.pipeline.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于文档结构边界（Markdown 标题、段落）将长文档拆分为语义完整的 token 预算分块。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Component
public class StructureAwareChunkTransformer implements DocumentTransformer {
    /** 分块策略标识，写入分块元数据 */
    private static final String STRATEGY = "structure-aware-token-budget-v1";
    /** 匹配连续空行，用于段落拆分 */
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");
    /** 匹配 Markdown ATX 标题行 */
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    /** 每 token 估算字符数（简化为 1:1） */
    private static final int CHARS_PER_TOKEN = 1;
    private final ChunkingProperties properties;

    /**
     * @param properties 分块配置属性
     */
    public StructureAwareChunkTransformer(ChunkingProperties properties) {
        this.properties = properties;
    }

    /**
     * @param documents 原始文档列表
     * @return 拆分后的分块文档列表
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Document> chunks = new ArrayList<>();
        for (Document document : documents) {
            chunks.addAll(chunkDocument(document));
        }
        return chunks;
    }

    // 对单个文档按章节拆分后构建分块列表
    private List<Document> chunkDocument(Document document) {
        if (document == null || document.getText() == null || document.getText().isBlank()) {
            return List.of();
        }
        String text = document.getText();
        List<ChunkRange> ranges = splitBySections(text).stream()
                .flatMap(section -> splitSection(text, section).stream())
                .toList();
        return buildDocuments(document, ranges);
    }

    // 收集标题并将文本按标题边界拆分为章节列表
    private List<Section> splitBySections(String text) {
        List<HeadingMatch> headings = collectHeadings(text);
        if (headings.isEmpty()) {
            return List.of(new Section(0, text.length(), ""));
        }
        List<Section> sections = new ArrayList<>();
        appendLeadingSection(text, headings.getFirst().start(), sections);
        for (int index = 0; index < headings.size(); index++) {
            HeadingMatch heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : text.length();
            appendSection(heading.start(), end, heading.title(), text, sections);
        }
        return sections;
    }

    // 遍历文本收集所有标题位置和内容，跳过代码块内的 '#' 行
    private List<HeadingMatch> collectHeadings(String text) {
        List<HeadingMatch> headings = new ArrayList<>();
        Fence fence = null;
        for (int start = 0; start < text.length(); ) {
            int end = text.indexOf('\n', start);
            end = end >= 0 ? end : text.length();
            String line = text.substring(start, end);
            if (fence == null) {
                fence = openFence(line);
                if (fence == null) {
                    HeadingMatch heading = parseHeading(line, start);
                    if (heading != null) {
                        headings.add(heading);
                    }
                }
            } else if (isClosingFence(line, fence)) {
                fence = null;
            }
            start = end < text.length() ? end + 1 : text.length();
        }
        return headings;
    }

    // 将第一个标题前的前导文本作为无标题章节加入列表
    private void appendLeadingSection(String text, int firstHeadingStart, List<Section> sections) {
        if (firstHeadingStart <= 0) return;
        Range range = trimRange(text, 0, firstHeadingStart);
        if (range != null) sections.add(new Section(range.start(), range.end(), ""));
    }

    // 将去空白后的有效文本范围以章节形式加入列表
    private void appendSection(int start, int end, String title, String text, List<Section> sections) {
        Range range = trimRange(text, start, end);
        if (range != null) sections.add(new Section(range.start(), range.end(), title));
    }

    // 若章节未超过最大 token 则直接返回，否则拆分后合并
    private List<ChunkRange> splitSection(String text, Section section) {
        return estimateTokens(text.substring(section.start(), section.end())) <= maxTokens()
                ? List.of(section.toChunkRange())
                : mergePieces(text, splitOversizedPieces(text, section));
    }

    // 将超大章节先按段落拆分，超限段落再按句子拆分
    private List<ChunkRange> splitOversizedPieces(String text, Section section) {
        List<ChunkRange> pieces = new ArrayList<>();
        for (ChunkRange paragraph : splitByParagraphs(text, section)) {
            String paragraphText = text.substring(paragraph.start(), paragraph.end());
            pieces.addAll(estimateTokens(paragraphText) <= maxTokens()
                    ? List.of(paragraph)
                    : splitBySentences(text, paragraph));
        }
        return splitRemainingOversized(text, pieces);
    }

    // 按连续空行将章节文本拆分为段落列表
    private List<ChunkRange> splitByParagraphs(String text, Section section) {
        Matcher matcher = BLANK_LINES.matcher(text.substring(section.start(), section.end()));
        List<ChunkRange> paragraphs = new ArrayList<>();
        int cursor = section.start();
        while (matcher.find()) {
            appendRange(text, cursor, section.start() + matcher.start(), section.path(), section.start(), paragraphs);
            cursor = section.start() + matcher.end();
        }
        appendRange(text, cursor, section.end(), section.path(), section.start(), paragraphs);
        return paragraphs;
    }

    // 按句子边界（中英文标点）将文本段切分为句子列表
    private List<ChunkRange> splitBySentences(String text, ChunkRange range) {
        List<ChunkRange> sentences = new ArrayList<>();
        int cursor = range.start();
        for (int index = range.start(); index < range.end(); index++) {
            if (isSentenceBoundary(text.charAt(index))) {
                appendRange(text, cursor, index + 1, range.path(), range.sectionStart(), sentences);
                cursor = index + 1;
            }
        }
        appendRange(text, cursor, range.end(), range.path(), range.sectionStart(), sentences);
        return sentences;
    }

    // 对仍然超过最大 token 的片段强制按字符数硬截断
    private List<ChunkRange> splitRemainingOversized(String text, List<ChunkRange> pieces) {
        List<ChunkRange> ranges = new ArrayList<>();
        for (ChunkRange piece : pieces) {
            boolean fits = estimateTokens(text.substring(piece.start(), piece.end())) <= maxTokens();
            ranges.addAll(fits ? List.of(piece) : splitByTokenBudget(text, piece));
        }
        return ranges;
    }

    // 按 {@code maxTokens * CHARS_PER_TOKEN} 固定步长硬截断
    private List<ChunkRange> splitByTokenBudget(String text, ChunkRange range) {
        List<ChunkRange> chunks = new ArrayList<>();
        int maxChars = Math.max(CHARS_PER_TOKEN, maxTokens() * CHARS_PER_TOKEN);
        for (int cursor = range.start(); cursor < range.end(); cursor += maxChars) {
            appendRange(text, cursor, Math.min(range.end(), cursor + maxChars), range.path(),
                    range.sectionStart(), chunks);
        }
        return chunks;
    }

    // 贪心合并相邻且不超过 token 预算的片段
    private List<ChunkRange> mergePieces(String text, List<ChunkRange> pieces) {
        List<ChunkRange> chunks = new ArrayList<>();
        ChunkRange current = null;
        for (ChunkRange piece : pieces) {
            if (current == null) {
                current = piece;
            } else if (canMerge(text, current, piece)) {
                current = new ChunkRange(current.start(), piece.end(), current.path(), current.sectionStart());
            } else {
                chunks.add(current);
                current = piece;
            }
        }
        if (current != null) {
            chunks.add(current);
        }
        return chunks;
    }

    // 判断两片段合并后是否不超过目标 token（或未达目标但不超过最大值）
    private boolean canMerge(String text, ChunkRange current, ChunkRange next) {
        String candidate = text.substring(current.start(), next.end());
        int currentTokens = estimateTokens(text.substring(current.start(), current.end()));
        return estimateTokens(candidate) <= targetTokens()
                || currentTokens < targetTokens() && estimateTokens(candidate) <= maxTokens();
    }

    // 根据分块范围列表和重叠配置构建最终 Document 列表
    private List<Document> buildDocuments(Document source, List<ChunkRange> ranges) {
        List<Document> chunks = new ArrayList<>();
        for (ChunkRange range : ranges) {
            int start = chunks.isEmpty() ? range.start() : overlapStart(range.start(), range.sectionStart());
            Range chunkRange = trimRange(source.getText(), start, range.end());
            if (chunkRange != null) {
                chunks.add(buildDocument(source, chunkRange, range.path(), chunks.size()));
            }
        }
        return chunks;
    }

    // 构建单个分块 Document，设置元数据（章节路径、字符范围、策略标识）和带索引的 ID
    private Document buildDocument(Document source, Range range, String sectionPath, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        metadata.put("sectionPath", sectionPath);
        metadata.put("charStart", range.start());
        metadata.put("charEnd", range.end());
        metadata.put("chunkStrategy", STRATEGY);
        String id = source.getId() == null ? null : source.getId() + ":" + chunkIndex;
        return Document.builder()
                .id(id)
                .text(source.getText().substring(range.start(), range.end()))
                .metadata(metadata)
                .build();
    }

    // 计算重叠起始位置，不低于章节起始位置
    private int overlapStart(int start, int sectionStart) {
        if (properties.getOverlapTokens() <= 0) return start;
        return Math.max(sectionStart, start - properties.getOverlapTokens() * CHARS_PER_TOKEN);
    }

    // 对文本范围去空白后包装为 ChunkRange 并加入列表
    private void appendRange(String text, int start, int end, String path,
                             int sectionStart, List<ChunkRange> ranges) {
        Range range = trimRange(text, start, end);
        if (range != null) ranges.add(new ChunkRange(range.start(), range.end(), path, sectionStart));
    }

    // 去除首尾空白后返回有效范围，若为空则返回 null
    private Range trimRange(String text, int start, int end) {
        int normalizedStart = Math.max(0, start);
        int normalizedEnd = Math.min(text.length(), end);
        while (normalizedStart < normalizedEnd && Character.isWhitespace(text.charAt(normalizedStart))) {
            normalizedStart++;
        }
        while (normalizedEnd > normalizedStart && Character.isWhitespace(text.charAt(normalizedEnd - 1))) {
            normalizedEnd--;
        }
        return normalizedStart < normalizedEnd ? new Range(normalizedStart, normalizedEnd) : null;
    }

    // 判断字符是否为中英文句子结束标点
    private boolean isSentenceBoundary(char character) { return "。！？；!?;".indexOf(character) >= 0; }
    // 去除标题末尾的空白及可选 '#'
    private String cleanHeading(String title) { return title.replaceAll("\\s+#*$", "").strip(); }

    // 解析单行是否为 Markdown 标题，是则返回匹配结果
    private HeadingMatch parseHeading(String line, int start) {
        Matcher matcher = HEADING.matcher(line);
        return matcher.matches() ? new HeadingMatch(start, cleanHeading(matcher.group(2))) : null;
    }

    // 解析单行是否为代码块起始分隔符（缩进不超过 3 且连续 3 个以上 ` 或 ~）
    private Fence openFence(String line) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent == line.length()) {
            return null;
        }
        char marker = line.charAt(indent);
        int length = fenceLength(line, indent, marker);
        return (marker == '`' || marker == '~') && length >= 3 ? new Fence(marker, length) : null;
    }

    // 判断单行是否为对应代码块的结束分隔符
    private boolean isClosingFence(String line, Fence fence) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent == line.length() || line.charAt(indent) != fence.marker()) {
            return false;
        }
        int length = fenceLength(line, indent, fence.marker());
        return length >= fence.length() && line.substring(indent + length).isBlank();
    }

    // 统计行首连续空格数
    private int leadingSpaces(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') index++;
        return index;
    }
    // 统计从指定位置起连续相同字符的长度
    private int fenceLength(String line, int start, char marker) {
        int index = start;
        while (index < line.length() && line.charAt(index) == marker) index++;
        return index - start;
    }
    // 返回目标 token 数（最小为 1）
    private int targetTokens() { return Math.max(1, properties.getTargetTokens()); }
    // 返回最大 token 数（不小于目标值）
    private int maxTokens() { return Math.max(targetTokens(), properties.getMaxTokens()); }
    // 估算文本 token 数（简化字符数映射）
    private int estimateTokens(String text) { return Math.max(1, text.length()); }

    /** 标题匹配结果 */
    private record HeadingMatch(int start, String title) {}
    /** 文本起止范围 */
    private record Range(int start, int end) {}
    /** 代码块分隔符 */
    private record Fence(char marker, int length) {}
    /** 章节信息，含标题路径 */
    private record Section(int start, int end, String path) {
        // 转换为分块范围
        private ChunkRange toChunkRange() { return new ChunkRange(start, end, path, start); }
    }
    /** 分块范围，记录所属章节路径和章节起始位置 */
    private record ChunkRange(int start, int end, String path, int sectionStart) {}
}
