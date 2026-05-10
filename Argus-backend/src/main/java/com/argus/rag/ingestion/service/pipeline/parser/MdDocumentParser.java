package com.argus.rag.ingestion.service.pipeline.parser;

import com.argus.rag.ingestion.service.pipeline.parser.TextDecodingSupport;

import java.io.InputStream;

/**
 * Markdown 文档解析器。
 *
 * <h3>支持的格式</h3>
 * <p>支持扩展名为 {@code .md} 的 Markdown 文件。</p>
 *
 * <h3>解析策略</h3>
 * <ol>
 *     <li>通过 {@link TextDecodingSupport} 自动检测编码并解码为原始 Markdown 字符串</li>
 *     <li>剥离 Markdown 语法标记，提取纯文本内容：
 *         <ul>
 *             <li>代码块（{@code ```}）替换为空格</li>
 *             <li>行内代码（{@code `code`}）保留代码文字</li>
 *             <li>图片链接（{@code ![alt](url)}）替换为空格</li>
 *             <li>普通链接（{@code [text](url)}）保留链接文字</li>
 *             <li>标题标记（{@code #}）移除</li>
 *             <li>引用标记（{@code >}）移除</li>
 *             <li>无序列表标记（{@code - * +}）移除</li>
 *             <li>有序列表序号（{@code 1.}）移除</li>
 *             <li>强调标记（{@code ** __ * ~}）移除</li>
 *         </ul>
 *     </li>
 *     <li>压缩多余空白和连续换行为合理的段落间距</li>
 *     <li>去除首尾空白</li>
 * </ol>
 *
 * <h3>异常处理</h3>
 * <p>如果编码检测失败或读取异常，将抛出包含 "Markdown 文档解析失败" 消息的
 * {@link com.argus.rag.common.exception.BusinessException}。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public class MdDocumentParser implements DocumentParser {

    /**
     * 判断是否支持指定扩展名（大小写不敏感）。
     *
     * @param extension 文件扩展名
     * @return 当扩展名为 "md"（忽略大小写）时返回 {@code true}
     */
    @Override
    public boolean supports(String extension) {
        return "md".equalsIgnoreCase(extension);
    }

    /**
     * 解析 Markdown 文件，提取纯文本内容。
     *
     * @param inputStream Markdown 文件的输入流
     * @return 剥离 Markdown 语法后的纯文本内容
     * @throws com.argus.rag.common.exception.BusinessException 当编码检测或解码失败时抛出
     */
    @Override
    public String parse(InputStream inputStream) {
        return stripMarkdown(TextDecodingSupport.decode(inputStream, "Markdown 文档解析失败")).trim();
    }

    /**
     * 剥离 Markdown 语法标记，提取纯文本。
     *
     * <p>处理顺序：先统一换行符，再依次移除代码块、行内代码、图片、链接、
     * 标题、引用、列表标记和强调标记，最后压缩多余空白和连续换行。</p>
     *
     * @param content 原始 Markdown 文本
     * @return 剥离语法标记后的纯文本
     */
    private String stripMarkdown(String content) {
        String plainText = content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                .replaceAll("(\\*\\*|__|[*_~])", "");
        return plainText.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
    }
}
