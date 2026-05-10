package com.argus.rag.ingestion.service.pipeline.transformer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档分块配置属性，绑定前缀 {@code ingestion.chunking}。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ingestion.chunking")
@Data
public class ChunkingProperties {

    /** 目标 token 数 */
    private int targetTokens = 500;
    /** 最大 token 数 */
    private int maxTokens = 800;
    /** 重叠 token 数 */
    private int overlapTokens = 80;

    /** 使用默认值构造。 */
    public ChunkingProperties() {
    }

    /**
     * @param targetTokens  目标 token 数
     * @param maxTokens     最大 token 数
     * @param overlapTokens 重叠 token 数
     */
    public ChunkingProperties(int targetTokens, int maxTokens, int overlapTokens) {
        this.targetTokens = targetTokens;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }
}
