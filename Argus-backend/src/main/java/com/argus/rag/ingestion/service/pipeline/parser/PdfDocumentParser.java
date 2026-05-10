package com.argus.rag.ingestion.service.pipeline.parser;

import com.argus.rag.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 文档解析器，基于 Apache PDFBox。
 *
 * <h3>支持的格式</h3>
 * <p>支持扩展名为 {@code .pdf} 的 PDF 文档。</p>
 *
 * <h3>解析策略</h3>
 * <ol>
 *     <li>使用 PDFBox 的 {@link PDDocument#load(InputStream)} 加载 PDF 文档</li>
 *     <li>通过 {@link PDFTextStripper} 提取全部文本内容（按页码顺序）</li>
 *     <li>统一换行符：将所有 {@code \r\n} 和独立的 {@code \r} 转换为 {@code \n}</li>
 *     <li>去除首尾空白字符</li>
 * </ol>
 *
 * <h3>异常处理</h3>
 * <p>PDFBox 在遇到损坏文件、加密文件或非 PDF 格式时可能抛出多种异常
 * （如 {@link IOException}），解析器统一包装为包含 "PDF 文档解析失败" 消息的
 * {@link com.argus.rag.common.exception.BusinessException}。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public class PdfDocumentParser implements DocumentParser {

    /**
     * 判断是否支持指定扩展名（大小写不敏感）。
     *
     * @param extension 文件扩展名
     * @return 当扩展名为 "pdf"（忽略大小写）时返回 {@code true}
     */
    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    /**
     * 解析 PDF 文件内容为纯文本。
     *
     * <p>使用 PDFBox 加载 PDF 文档并通过文本剥离器提取全部文本。
     * 解析完成后会自动关闭 PDF 文档资源。</p>
     *
     * @param inputStream PDF 文件的输入流
     * @return 解析后的纯文本内容
     * @throws com.argus.rag.common.exception.BusinessException 当 PDF 文件损坏、
     *         格式非法或读取异常时抛出
     */
    @Override
    public String parse(InputStream inputStream) {
        try (PDDocument document = PDDocument.load(inputStream)) {
            String text = new PDFTextStripper().getText(document);
            return text.replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (IOException exception) {
            throw new BusinessException("PDF 文档解析失败", exception);
        }
    }
}
