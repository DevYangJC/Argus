package com.argus.rag.ingestion.service.pipeline.parser;

import java.io.InputStream;

/**
 * 文档解析器接口，定义了解析器的核心契约。
 *
 * <h3>实现要求</h3>
 * <ul>
 *     <li>{@link #supports(String)} —— 声明当前解析器支持的文档格式。
 *         扩展名匹配应忽略大小写（如 "TXT" 与 "txt" 应同等对待）。</li>
 *     <li>{@link #parse(InputStream)} —— 将输入流中的文档内容解析为纯文本字符串。
 *         实现类应自行管理输入流的关闭，并在解析失败时抛出明确的业务异常。</li>
 * </ul>
 *
 * <h3>职责边界</h3>
 * <p>解析器只负责将特定格式的文档转为纯文本，不涉及文本清洗、切片等后续处理。
 * 编码检测、Markdown 语法剥离等辅助逻辑应委托给独立的工具类。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持指定的文件扩展名。
     *
     * @param extension 文件扩展名，不含前导点（如 "txt"、"pdf"）
     * @return {@code true} 表示支持该格式
     */
    boolean supports(String extension);

    /**
     * 将输入流中的文档解析为纯文本字符串。
     *
     * @param inputStream 文档文件的输入流，由调用方负责关闭
     * @return 解析后的纯文本内容
     * @throws com.argus.rag.common.exception.BusinessException 当解析过程发生错误时抛出，
     *         异常消息应指明具体文档类型
     */
    String parse(InputStream inputStream);
}
