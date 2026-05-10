package com.argus.rag.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / SpringDoc OpenAPI 配置，支持 Bearer JWT 认证。
 * <p>
 * 页面右上角点 🔒 Authorize，粘贴 accessToken（纯 token，不加 Bearer 前缀），
 * 点 Authorize 确认后所有接口自动携带 Authorization header。
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
