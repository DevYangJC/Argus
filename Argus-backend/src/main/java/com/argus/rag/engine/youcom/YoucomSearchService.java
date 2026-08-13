package com.argus.rag.engine.youcom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.ai.document.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * You.com 网络搜索服务。
 * <p>
 * 通过 YDC Index API ({@code https://ydc-index.io/v1/search}) 执行网络搜索，
 * 将搜索结果转换为 Spring AI {@link Document} 对象，
 * 追加到检索证据束中作为第三条检索通道。
 * </p>
 *
 * <p>通信方式：通过 JDK 原生 {@link HttpClient} 直接调用 YDC Index REST API。</p>
 *
 * @author Argus-RAG Team
 */
@Service
@Slf4j
public class YoucomSearchService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String SEARCH_URL = "https://ydc-index.io/v1/search";

    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean enabled;
    private final int topK;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 构造 You.com 搜索服务。
     *
     * @param apiKey You.com API 密钥，从环境变量或配置读取
     * @param enabled 是否启用 You.com 搜索通道
     * @param topK 每次搜索返回的最大结果数
     * @param objectMapper Jackson ObjectMapper，用于 JSON 解析
     */
    @Autowired
    public YoucomSearchService(
            @Value("${youcom.search.api-key:}") String apiKey,
            @Value("${youcom.search.enabled:false}") boolean enabled,
            @Value("${youcom.search.top-k:10}") int topK,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.topK = Math.max(1, topK);
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 You.com 网络搜索。
     *
     * @param query 搜索关键词 / 问题文本
     * @return 搜索结果列表，每个结果为 Spring AI {@link Document}，content 取自 snippet，url 写入 metadata
     */
    public List<Document> search(String query) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            log.debug("You.com search disabled or API key not configured, skipping");
            return List.of();
        }

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        long startNano = System.nanoTime();
        try {
            String requestBody = """
                    {
                        "query": %s,
                        "count": %d
                    }
                    """.formatted(toJsonString(query), topK);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SEARCH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429) {
                log.warn("You.com search rate limit exceeded (429)");
                return List.of();
            }
            if (status == 401) {
                log.warn("You.com API key invalid or expired (401)");
                return List.of();
            }
            if (status != 200) {
                log.warn("You.com search failed with status {}: {}", status, response.body());
                return List.of();
            }

            List<Document> documents = parseResponse(response.body());
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            log.info("You.com search completed: query={}, results={}, elapsedMs={}",
                    query, documents.size(), elapsedMs);
            return documents;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("You.com search interrupted: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            log.error("You.com search error: {} (elapsedMs={})", e.getMessage(), elapsedMs);
            return List.of();
        }
    }

    private List<Document> parseResponse(String responseBody) {
        List<Document> documents = new ArrayList<>();
        try {
            var node = objectMapper.readTree(responseBody);
            var results = node.path("results");
            if (!results.isArray()) {
                return documents;
            }

            int index = 1;
            for (var item : results) {
                String title = item.path("title").asText("");
                String url = item.path("url").asText("");
                String content = extractSnippet(item);
                if (content.isEmpty()) {
                    content = item.path("description").asText("");
                }

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("evidenceId", "WEB" + index);
                metadata.put("retrievalSource", "YOUCOM");
                metadata.put("score", 1.0 / index);
                metadata.put("url", url);
                metadata.put("title", title);
                metadata.put("coverageMode", "WEB_SEARCH");

                String evidenceText = "来源：" + title + "\nURL：" + url + "\n" + content;
                documents.add(Document.builder()
                        .id("WEB" + index)
                        .text(evidenceText)
                        .metadata(metadata)
                        .build());
                index++;
            }
        } catch (Exception e) {
            log.error("Failed to parse You.com response: {}", e.getMessage());
        }
        return documents;
    }

    private String extractSnippet(com.fasterxml.jackson.databind.JsonNode item) {
        var snippets = item.path("snippets");
        if (snippets.isArray() && !snippets.isEmpty()) {
            return snippets.get(0).asText("");
        }
        return "";
    }

    private String toJsonString(String text) {
        try {
            return objectMapper.writeValueAsString(text);
        } catch (Exception e) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
