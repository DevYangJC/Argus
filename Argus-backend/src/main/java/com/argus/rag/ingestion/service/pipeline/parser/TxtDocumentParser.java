package com.argus.rag.ingestion.service.pipeline.parser;

import com.argus.rag.ingestion.service.pipeline.parser.TextDecodingSupport;

import java.io.InputStream;

/**
 * TXT 纯文本文档解析器。
 *
 * <h3>支持的格式</h3>
 * <p>支持扩展名为 {@code .txt} 的纯文本文件。</p>
 *
 * <h3>解析策略</h3>
 * <ol>
 *     <li>通过 {@link TextDecodingSupport} 自动检测文本编码（UTF-8 BOM、UTF-16 BOM、
 *         无 BOM UTF-8、GB18030）并解码为字符串</li>
 *     <li>统一换行符：将所有 {@code \r\n} 和独立的 {@code \r} 转换为 {@code \n}</li>
 *     <li>去除首尾空白字符</li>
 * </ol>
 *
 * <h3>异常处理</h3>
 * <p>如果编码检测失败或读取异常，将抛出包含 "TXT 文档解析失败" 消息的
 * {@link com.argus.rag.common.exception.BusinessException}。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public class TxtDocumentParser implements DocumentParser {

    /**
     * 判断是否支持指定扩展名（大小写不敏感）。
     *
     * @param extension 文件扩展名
     * @return 当扩展名为 "txt"（忽略大小写）时返回 {@code true}
     */
    @Override
    public boolean supports(String extension) {
        return "txt".equalsIgnoreCase(extension);
    }

    /**
     * 解析 TXT 文件内容为纯文本。
     *
     * @param inputStream TXT 文件的输入流
     * @return 换行符统一为 {@code \n} 且去除首尾空白的纯文本
     * @throws com.argus.rag.common.exception.BusinessException 当编码检测或解码失败时抛出
     */
    @Override
    public String parse(InputStream inputStream) {
        return normalizeText(TextDecodingSupport.decode(inputStream, "TXT 文档解析失败"));
    }

    /**
     * 规范化文本：统一换行符、去除首尾空白。
     *
     * @param content 原始文本内容
     * @return 规范化后的文本
     */
    private String normalizeText(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
