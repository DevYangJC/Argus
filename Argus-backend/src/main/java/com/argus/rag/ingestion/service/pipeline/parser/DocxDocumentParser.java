package com.argus.rag.ingestion.service.pipeline.parser;

import com.argus.rag.common.exception.BusinessException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * DOCX 文档解析器，基于 Apache POI。
 *
 * <h3>支持的格式</h3>
 * <p>支持扩展名为 {@code .docx} 的 Word 文档（Office Open XML 格式）。
 * 注意：不支持旧版 {@code .doc} 格式（OLE2 复合文档格式）。</p>
 *
 * <h3>解析策略</h3>
 * <ol>
 *     <li>使用 Apache POI 的 {@link XWPFDocument} 加载 DOCX 文档</li>
 *     <li>通过 {@link XWPFWordExtractor} 提取全部文本内容（按段落顺序）</li>
 *     <li>统一换行符：将所有 {@code \r\n} 和独立的 {@code \r} 转换为 {@code \n}</li>
 *     <li>去除首尾空白字符</li>
 * </ol>
 *
 * <h3>异常处理</h3>
 * <p>针对不同的异常类型给出不同的错误消息：</p>
 * <ul>
 *     <li>{@link NotOfficeXmlFileException} 或 {@link ZipException} —— 提示文件不是合法 Word 文档</li>
 *     <li>{@link IOException} 及其他 I/O 异常 —— 提示解析失败</li>
 * </ul>
 * <p>所有异常统一包装为
 * {@link com.argus.rag.common.exception.BusinessException}。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public class DocxDocumentParser implements DocumentParser {

    /**
     * 判断是否支持指定扩展名（大小写不敏感）。
     *
     * @param extension 文件扩展名
     * @return 当扩展名为 "docx"（忽略大小写）时返回 {@code true}
     */
    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }

    /**
     * 解析 DOCX 文件内容为纯文本。
     *
     * <p>使用 Apache POI 加载 DOCX 并通过提取器获取全部文本。
     * 解析完成后会自动关闭文档资源。</p>
     *
     * @param inputStream DOCX 文件的输入流
     * @return 解析后的纯文本内容
     * @throws com.argus.rag.common.exception.BusinessException 当文件不是合法的 DOCX
     *         格式或读取异常时抛出
     */
    @Override
    public String parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (NotOfficeXmlFileException | ZipException exception) {
            throw new BusinessException("无效 DOCX 文件：文件内容不是合法的 Word 文档", exception);
        } catch (IOException exception) {
            throw new BusinessException("DOCX 文档解析失败", exception);
        }
    }
}
