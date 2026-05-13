package com.argus.rag.qa.controller;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.common.exception.ForbiddenException;
import com.argus.rag.qa.model.dto.AskQuestionRequest;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.service.QaChatService;
import com.argus.rag.qa.service.QaService;
import com.argus.rag.qa.support.CitationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link QaController} 单元测试。
 * <p>
 * 使用 {@link WebMvcTest} 仅加载 Controller 层，模拟 Service 层依赖，
 * 重点测试 SSE 流式接口的各类场景。
 * </p>
 */
@WebMvcTest(QaController.class)
@DisplayName("QaController SSE 流式接口测试")
class QaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QaService qaService;

    @MockitoBean
    private CitationAssembler citationAssembler;

    private static final String STREAM_URL = "/api/qa/stream-ask";

    /** 构造一个有效的请求体 JSON */
    private static String validRequestJson() {
        return "{\"groupId\": 1, \"question\": \"上传流程是什么？\"}";
    }

    /** 构造 groupId 为 null 的请求体 JSON */
    private static String nullGroupIdRequestJson() {
        return "{\"groupId\": null, \"question\": \"上传流程是什么？\"}";
    }

    /** 构造 question 为空白的请求体 JSON */
    private static String blankQuestionRequestJson() {
        return "{\"groupId\": 1, \"question\": \"\"}";
    }

    /**
     * 创建模拟的 StreamContext。
     *
     * @param tokenFlux 模拟的 token 流
     * @return StreamContext 实例
     */
    private static QaChatService.StreamContext streamContext(Flux<String> tokenFlux) {
        return new QaChatService.StreamContext(tokenFlux, List.of());
    }

    /**
     * 创建模拟的 StreamContext，附带检索文档。
     */
    private static QaChatService.StreamContext streamContext(
            Flux<String> tokenFlux,
            List<Document> documents) {
        return new QaChatService.StreamContext(tokenFlux, documents);
    }

    @BeforeEach
    void setUp() {
        // 默认 CitationAssembler 返回空列表
        when(citationAssembler.assembleDocuments(any())).thenReturn(List.of());
    }

    // ──────────────────────────────────────────────
    // 正常流式回答场景
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("正常流式回答")
    class HappyPath {

        @Test
        @DisplayName("应逐 token 推送并在完成后发送 citations 事件")
        void shouldStreamTokensAndSendCitationsOnComplete() throws Exception {
            List<Document> documents = List.of(
                    new Document("证据内容", Map.of("documentId", 1L, "chunkId", 10L, "chunkIndex", 0, "fileName",
                            "doc.pdf", "score", 0.95)));
            List<AskQuestionResponse.Citation> citations = List.of(
                    new AskQuestionResponse.Citation(1L, 10L, 0, "doc.pdf", 0.95, null));

            when(citationAssembler.assembleDocuments(documents)).thenReturn(citations);
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(
                            Flux.just("上传流程", "分", "三个阶段", "：", "准备、", "上传、", "验证。"),
                            documents));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .contains("event:token")
                    .contains("data:上传流程")
                    .contains("data:三个阶段")
                    .contains("event:citations")
                    .contains("doc.pdf");
        }

        @Test
        @DisplayName("无引用来源时应仅推送 token 不发送 citations 事件")
        void shouldStreamTokensWithoutCitationsWhenNoneAvailable() throws Exception {
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(Flux.just("简短回答。")));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .contains("event:token")
                    .contains("data:简短回答。")
                    .doesNotContain("event:citations");
        }

        @Test
        @DisplayName("tokens 较多时应全部推送")
        void shouldStreamAllTokensCorrectly() throws Exception {
            Flux<String> tokenFlux = Flux.just(
                    "首", "先", "，", "你", "需", "要", "准", "备", "好",
                    "文", "件", "。", "然", "后", "点", "击", "上", "传", "按", "钮", "。");
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(tokenFlux));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 验证所有 token 都出现在响应中
            assertThat(responseBody).contains("data:首");
            assertThat(responseBody).contains("data:按钮。");
            // 统计 token 事件数量
            long tokenEventCount = responseBody.lines()
                    .filter(line -> line.startsWith("event:token"))
                    .count();
            assertThat(tokenEventCount).isEqualTo(22);
        }
    }

    // ──────────────────────────────────────────────
    // 无证据场景
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("无证据可答")
    class NoEvidence {

        @Test
        @DisplayName("无证据时应发送 error 事件而非 token 事件")
        void shouldSendErrorEventWhenNoEvidence() throws Exception {
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(
                            Flux.error(new BusinessException("INSUFFICIENT_EVIDENCE: 检索到的有效证据不足，暂不回答。"))));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .contains("event:error")
                    .contains("INSUFFICIENT_EVIDENCE")
                    .doesNotContain("event:token");
        }
    }

    // ──────────────────────────────────────────────
    // 权限校验失败场景
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("权限校验失败")
    class PermissionDenied {

        @Test
        @DisplayName("非群组成员应发送 error 事件")
        void shouldSendErrorEventWhenPermissionDenied() throws Exception {
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenThrow(new ForbiddenException("当前用户不是目标群组成员"));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .contains("event:error")
                    .contains("当前用户不是目标群组成员");
        }
    }

    // ──────────────────────────────────────────────
    // 大模型流式调用异常场景
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("大模型调用异常")
    class LlmError {

        @Test
        @DisplayName("LLM 调用中期出错时应发送 error 事件")
        void shouldSendErrorEventWhenLlmFailsMidStream() throws Exception {
            // 先正常发送几个 token，然后报错
            Flux<String> failingFlux = Flux.just("正在", "处理")
                    .concatWith(Flux.error(new RuntimeException("大模型调用超时")));

            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(failingFlux));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .contains("event:token")
                    .contains("data:正在")
                    .contains("event:error")
                    .contains("大模型调用超时");
        }

        @Test
        @DisplayName("LLM 调用立即失败时应仅发送 error 事件")
        void shouldSendOnlyErrorEventWhenLlmFailsImmediately() throws Exception {
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(
                            Flux.error(new RuntimeException("模型不可用"))));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .contains("event:error")
                    .contains("模型不可用")
                    .doesNotContain("event:token");
        }
    }

    // ──────────────────────────────────────────────
    // 参数校验场景
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("groupId 为 null 时应返回 400")
        void shouldReturn400WhenGroupIdIsNull() throws Exception {
            mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(nullGroupIdRequestJson()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("question 为空时应返回 400")
        void shouldReturn400WhenQuestionIsBlank() throws Exception {
            mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(blankQuestionRequestJson()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────
    // 空 Flux 场景（边缘情况）
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("边缘情况")
    class EdgeCases {

        @Test
        @DisplayName("token 流为空时应正常完成但不发送任何 token")
        void shouldCompleteGracefullyWhenTokenFluxIsEmpty() throws Exception {
            when(qaService.askStream(any(), any(AskQuestionRequest.class)))
                    .thenReturn(streamContext(Flux.empty()));

            MvcResult mvcResult = mockMvc.perform(post(STREAM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequestJson()))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String responseBody = mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                    .doesNotContain("event:token")
                    .doesNotContain("event:error");
        }
    }
}
