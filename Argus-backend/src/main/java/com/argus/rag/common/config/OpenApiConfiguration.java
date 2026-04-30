package com.argus.rag.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
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

    private static final String SCHEME_NAME = "BearerAuth";

    @Bean
    OpenAPI argusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Argus API")
                        .description("Argus 知识库平台接口文档")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /** 逐个操作注入 SecurityRequirement，确保 Knife4j 每个接口都随请求带 Authorization */
//    @Bean
//    GlobalOpenApiCustomizer applySecurityToAllOperations() {
//        return openApi -> openApi.getPaths().values().forEach(pathItem ->
//                pathItem.readOperations().forEach(operation ->
//                        operation.addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))));
//    }
}
