package com.argus.rag.ingestion.config;

import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.ingestion.service.pipeline.ChunkService;
import com.argus.rag.ingestion.service.pipeline.parser.DocumentParserFactory;
import com.argus.rag.ingestion.service.DocumentIngestionProcessor;
import com.argus.rag.ingestion.service.EtlDocumentIngestionProcessor;
import com.argus.rag.ingestion.service.pipeline.transformer.StructureAwareChunkTransformer;
import com.argus.rag.ingestion.service.pipeline.transformer.TextCleanupTransformer;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import com.argus.rag.engine.storage.ObjectStorageService;
import com.argus.rag.ingestion.service.pipeline.transformer.ChunkingProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档摄入模块的 Spring 配置类。
 *
 * <h3>职责</h3>
 * <p>负责装配文档 ETL 流水线所需的全部 Bean，包括：</p>
 * <ul>
 *     <li>{@link DocumentIngestionProcessor} —— 文档摄入主处理器</li>
 *     <li>{@link TextCleanupTransformer} —— 文本清洗转换器
 *         （仅在容器中不存在同类型 Bean 时才创建默认实例）</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *     <li>使用 {@code proxyBeanMethods = false} 避免 CGLIB 代理，提升启动性能</li>
 *     <li>通过 {@link ObjectProvider} 实现延迟依赖解析，允许其他模块在文档
 *         模块之后加载而不会出现循环依赖</li>
 *     <li>{@link #requireBean(ObjectProvider, Class)} 在依赖缺失时快速失败，
 *         抛出明确的 {@link IllegalStateException} 而非 NPE</li>
 * </ul>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChunkingProperties.class)
public class DocumentIngestionConfiguration {

    /**
     * 创建文档摄入主处理器 Bean。
     *
     * <p>通过 {@link ObjectProvider} 收集所有必要依赖，
     * 组装 {@link EtlDocumentIngestionProcessor} 实例。
     * 任一必需依赖缺失时在启动阶段快速失败。</p>
     *
     * @param documentMapperProvider           文档数据访问层提供者
     * @param storageServiceProvider           对象存储服务提供者
     * @param parserFactoryProvider            文档解析器工厂提供者
     * @param textCleanupTransformerProvider   文本清洗转换器提供者
     * @param chunkTransformerProvider         结构感知切片转换器提供者
     * @param chunkServiceProvider             切片存储服务提供者
     * @param vectorServiceProvider            向量写入服务提供者
     * @return 组装完成的 {@link EtlDocumentIngestionProcessor} 实例
     * @throws IllegalStateException 当任一必需依赖缺失时抛出
     */
    @Bean
    DocumentIngestionProcessor documentIngestionProcessor(
            ObjectProvider<DocumentMapper> documentMapperProvider,
            ObjectProvider<ObjectStorageService> storageServiceProvider,
            ObjectProvider<DocumentParserFactory> parserFactoryProvider,
            ObjectProvider<TextCleanupTransformer> textCleanupTransformerProvider,
            ObjectProvider<StructureAwareChunkTransformer> chunkTransformerProvider,
            ObjectProvider<ChunkService> chunkServiceProvider,
            ObjectProvider<VectorIngestionService> vectorServiceProvider
    ) {
        return new EtlDocumentIngestionProcessor(
                requireBean(documentMapperProvider, DocumentMapper.class),
                requireBean(storageServiceProvider, ObjectStorageService.class),
                requireBean(parserFactoryProvider, DocumentParserFactory.class),
                requireBean(textCleanupTransformerProvider, TextCleanupTransformer.class),
                requireBean(chunkTransformerProvider, StructureAwareChunkTransformer.class),
                requireBean(chunkServiceProvider, ChunkService.class),
                requireBean(vectorServiceProvider, VectorIngestionService.class)
        );
    }

    /**
     * 创建默认的文本清洗转换器 Bean。
     *
     * <p>标注了 {@link ConditionalOnMissingBean}，允许项目的其他模块
     * 提供自定义实现来覆盖此默认值。</p>
     *
     * @return 默认的 {@link TextCleanupTransformer} 实例
     */
    @Bean
    @ConditionalOnMissingBean(TextCleanupTransformer.class)
    TextCleanupTransformer textCleanupTransformer() {
        return new TextCleanupTransformer();
    }

    /**
     * 从 {@link ObjectProvider} 中获取必需的 Bean，缺失时快速失败。
     *
     * @param provider Bean 提供者
     * @param beanType 期望的 Bean 类型
     * @param <T>      Bean 的具体类型
     * @return 从容器中获取的 Bean 实例
     * @throws IllegalStateException 当指定类型的 Bean 不存在时抛出
     */
    private <T> T requireBean(ObjectProvider<T> provider, Class<T> beanType) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new IllegalStateException(
                    "Failed to create EtlDocumentIngestionProcessor, missing required bean: "
                            + beanType.getSimpleName()
            );
        }
        return bean;
    }
}
