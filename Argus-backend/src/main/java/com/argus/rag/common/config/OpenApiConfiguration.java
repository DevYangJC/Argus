package com.argus.rag.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / SpringDoc OpenAPI 配置。
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI argusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Argus API")
                        .description("Argus 知识库平台接口文档")
                        .version("1.0.0"));
    }
}
