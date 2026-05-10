package com.argus.rag.ingestion.service.pipeline.parser;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.ingestion.service.pipeline.parser.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文档解析器工厂，负责管理文件扩展名与解析器之间的映射关系。
 *
 * <h3>注册机制</h3>
 * <p>工厂使用 {@code extension -> parser} 的映射表维护所有可用的解析器。
 * 通过 {@link DocumentParser#supports(String)} 方法判断解析器是否支持指定扩展名，
 * 只有解析器声明支持该扩展名时才会注册。同一个扩展名只会保留一个解析器
 * （后注册的会覆盖先注册的）。
 *
 * <h3>如何添加新的解析器</h3>
 * <ol>
 *     <li>新建一个实现 {@link DocumentParser} 接口的类（如 {@code CsvDocumentParser}）</li>
 *     <li>在 {@link #DocumentParserFactory()} 无参构造器中将其加入解析器列表</li>
 *     <li>确保 {@link DocumentParser#supports(String)} 返回 {@code true} 的目标扩展名已在
 *         {@link #DocumentParserFactory(List)} 的注册循环中列出（如 "csv"）</li>
 *     <li>或在调用处通过有参构造器注入自定义解析器列表</li>
 * </ol>
 *
 * <h3>扩展名规范化</h3>
 * <p>解析器获取前会对扩展名进行规范化处理：去除前导的点（{@code .}）并转为小写英文，
 * 确保 {@code .TXT}、{@code TXT}、{@code txt} 等输入均能正确匹配。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Component
public class DocumentParserFactory {

    /** 扩展名到解析器的映射表，保持注册顺序 */
    private final Map<String, DocumentParser> parserByExtension = new LinkedHashMap<>();

    /**
     * 无参构造器，默认注册 TXT、Markdown、PDF、DOCX 四种解析器。
     *
     * <p>使用 Spring 组件扫描时会自动调用此构造器。
     */
    public DocumentParserFactory() {
        this(List.of(
                new TxtDocumentParser(),
                new MdDocumentParser(),
                new PdfDocumentParser(),
                new DocxDocumentParser()
        ));
    }

    /**
     * 有参构造器，通过解析器列表进行注册。
     *
     * <p>遍历给定的解析器列表，依次尝试注册 "{@code txt}"、"{@code md}"、
     * "{@code pdf}"、"{@code docx}" 四种扩展名。只有解析器的 {@link DocumentParser#supports(String)}
     * 返回 {@code true} 时才会将该扩展名与解析器关联。
     *
     * @param parsers 要注册的解析器列表
     */
    public DocumentParserFactory(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            register("txt", parser);
            register("md", parser);
            register("pdf", parser);
            register("docx", parser);
        }
    }

    /**
     * 根据文件扩展名获取对应的文档解析器。
     *
     * <p>扩展名会先经过规范化处理：去除前导点、转为小写。
     *
     * @param extension 文件扩展名，如 "txt"、"pdf"、".docx"
     * @return 匹配的文档解析器
     * @throws BusinessException 当扩展名为空或不支持该类型时抛出
     */
    public DocumentParser getParser(String extension) {
        String normalizedExtension = normalizeExtension(extension);
        DocumentParser parser = parserByExtension.get(normalizedExtension);
        if (parser == null) {
            throw new BusinessException("不支持的文档类型: " + normalizedExtension);
        }
        return parser;
    }

    /**
     * 将解析器注册到指定扩展名。
     *
     * <p>只有当解析器的 {@link DocumentParser#supports(String)} 对指定扩展名返回 {@code true}
     * 时才会执行注册。
     *
     * @param extension 要注册的扩展名
     * @param parser    待注册的解析器
     */
    private void register(String extension, DocumentParser parser) {
        if (parser.supports(extension)) {
            parserByExtension.put(extension, parser);
        }
    }

    /**
     * 规范化文件扩展名：去除前导点、统一转为小写英文。
     *
     * @param extension 原始扩展名，可能包含前导点和大写字母
     * @return 规范化后的扩展名（纯小写、无前导点）
     * @throws BusinessException 当扩展名为 null 或空白时抛出
     */
    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BusinessException("文档扩展名不能为空");
        }
        return extension.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
    }
}
