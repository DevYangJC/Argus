package com.argus.rag.qa.service;

import com.argus.rag.qa.model.EvidenceLevel;
import com.argus.rag.qa.model.KnowledgeAnswerOutput;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.rag.ReadyChunkDocumentRetriever;
import com.argus.rag.qa.rag.RetrievedEvidenceBundle;
import com.argus.rag.qa.support.CitationAssembler;
import com.argus.rag.qa.support.QaAnswerParser;
import com.argus.rag.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 知识问答对话服务。
 * <p>
 * 负责执行完整的 RAG 问答流程：
 * 检索证据 → 证据充分度评估 → 构造 Prompt → 调用大模型生成结构化回答 → 组装引用。
 * 支持结构化输出失败时的原文解析回退机制。
 * </p>
 */
@Service
public class QaChatService {

        private static final Logger log = LoggerFactory.getLogger(QaChatService.class);

        /** 拒答原因编码：证据不足 */
        private static final String INSUFFICIENT_CODE = "INSUFFICIENT_EVIDENCE";
        /** 拒答原因描述：证据不足 */
        private static final String INSUFFICIENT_MESSAGE = "检索到的有效证据不足，暂不回答。";
        /** 拒答原因编码：回答格式错误 */
        private static final String FORMAT_ERROR_CODE = "ANSWER_FORMAT_ERROR";
        /** 拒答原因描述：回答格式错误 */
        private static final String FORMAT_ERROR_MESSAGE = "模型返回格式错误，无法解析回答。";

        private static final String MODEL_NAME = "qwen-plus";

        private final ChatClient qaChatClient;
        private final PromptTemplate qaUserPromptTemplate;
        private final ReadyChunkDocumentRetriever documentRetriever;
        private final QaAnswerParser answerParser;
        private final CitationAssembler citationAssembler;
        private final ObjectMapper objectMapper;

        /**
         * 用量信息。
         */
        public record UsageInfo(Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                boolean estimated, Long latencyMs) {
        }

        /**
         * 带用量信息的问答结果。
         */
        public record AskResult(AskQuestionResponse response, UsageInfo usage) {
        }

        /**
         * 内部 LLM 调用结果。
         */
        private record LlmCallResult(KnowledgeAnswerOutput output, UsageInfo usage) {
        }

        /**
         * 构造函数。
         *
         * @param qaChatClient         问答专用的 ChatClient
         * @param qaUserPromptTemplate 用户提示词模板
         * @param documentRetriever    文档检索器
         * @param answerParser         回答解析器（用于回退解析）
         * @param citationAssembler    引用组装器
         * @param objectMapper         JSON 序列化/反序列化工具
         */
        public QaChatService(
                        ChatClient qaChatClient,
                        @Qualifier("qaUserPromptTemplate") PromptTemplate qaUserPromptTemplate,
                        ReadyChunkDocumentRetriever documentRetriever,
                        QaAnswerParser answerParser,
                        CitationAssembler citationAssembler,
                        ObjectMapper objectMapper) {
                this.qaChatClient = qaChatClient;
                this.qaUserPromptTemplate = qaUserPromptTemplate;
                this.documentRetriever = documentRetriever;
                this.answerParser = answerParser;
                this.citationAssembler = citationAssembler;
                this.objectMapper = objectMapper;
        }

        /**
         * 执行知识问答流程。
         * <p>
         * 1. 检索相关证据文档；<br>
         * 2. 若无证据，直接返回拒答响应；<br>
         * 3. 调用大模型生成结构化回答；<br>
         * 4. 解析失败时返回格式错误响应；<br>
         * 5. 模型拒答时返回拒答响应；<br>
         * 6. 成功回答时组装引用来源。
         * </p>
         *
         * @param groupId  群组 ID
         * @param question 用户问题
         * @return 问答响应
         */
        /**
         * 执行知识问答流程（保留旧签名以保持兼容）。
         */
        public AskQuestionResponse ask(Long groupId, String question) {
                return askWithUsage(groupId, question).response();
        }

        /**
         * 执行知识问答流程，返回带用量信息的结果。
         */
        public AskResult askWithUsage(Long groupId, String question) {
                long startNano = System.nanoTime();
                long startMs = System.currentTimeMillis();
                log.info("问答请求开始: groupId={}, questionLength={}", groupId, question != null ? question.length() : 0);
                RetrievedEvidenceBundle evidenceBundle = documentRetriever.retrieveEvidence(groupId, question);
                List<Document> documents = evidenceBundle.documents();
                log.info("证据检索完成: groupId={}, evidenceCount={}, evidenceLevel={}",
                                groupId, documents.size(), evidenceBundle.evidenceLevel());
                if (documents.isEmpty()) {
                        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                        log.info("问答无证据可答: groupId={}, elapsedMs={}",
                                        groupId, elapsedMs);
                        return new AskResult(
                                        AskQuestionResponse.unanswered(INSUFFICIENT_CODE, INSUFFICIENT_MESSAGE, List.of()),
                                        new UsageInfo(0, 0, 0, false, elapsedMs));
                }
                LlmCallResult result = getStructuredAnswer(groupId, question, evidenceBundle);
                long latencyMs = System.currentTimeMillis() - startMs;
                if (result.output() == null) {
                        log.warn("问答结构化输出失败: groupId={}, evidenceCount={}", groupId, documents.size());
                        return new AskResult(
                                        AskQuestionResponse.unanswered(FORMAT_ERROR_CODE, FORMAT_ERROR_MESSAGE, List.of()),
                                        new UsageInfo(result.usage.promptTokens(), result.usage.completionTokens(),
                                                        result.usage.totalTokens(), result.usage.estimated(), latencyMs));
                }
                if (!result.output().answered() || !StringUtils.hasText(result.output().answer())) {
                        log.info("模型拒答: groupId={}, reasonCode={}, reasonMessage={}",
                                        groupId, result.output().reasonCode(), result.output().reasonMessage());
                        return new AskResult(
                                        AskQuestionResponse.unanswered(result.output().reasonCode(),
                                                        result.output().reasonMessage(), List.of()),
                                        new UsageInfo(result.usage.promptTokens(), result.usage.completionTokens(),
                                                        result.usage.totalTokens(), result.usage.estimated(), latencyMs));
                }
                long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                log.info("问答请求完成: groupId={}, answerLength={}, citationCount={}, elapsedMs={}",
                                groupId, result.output().answer().length(), documents.size(), elapsedMs);
                return new AskResult(
                                AskQuestionResponse.answered(
                                                result.output().answer().trim(),
                                                citationAssembler.assembleDocuments(documents)),
                                new UsageInfo(result.usage.promptTokens(), result.usage.completionTokens(),
                                                result.usage.totalTokens(), result.usage.estimated(), latencyMs));
        }

        /**
         * 调用大模型获取结构化回答。
         * <p>
         * 先通过 {@code chatResponse()} 获取完整响应以提取 usage，
         * 再手动解析为结构化输出对象；失败时回退为原文解析方式。
         * </p>
         */
        private LlmCallResult getStructuredAnswer(
                        Long groupId,
                        String question,
                        RetrievedEvidenceBundle evidenceBundle) {
                Prompt userPrompt = createUserPrompt(question, evidenceBundle);
                try {
                        ChatResponse chatResponse = qaChatClient.prompt(userPrompt)
                                        .advisors(advisor -> advisor
                                                        .param("groupId", groupId)
                                                        .param(
                                                                        ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                                                        evidenceBundle.documents()))
                                        .call()
                                        .chatResponse();
                        String text = chatResponse.getResult().getOutput().getText();
                        UsageInfo usageInfo = extractUsageInfo(chatResponse.getMetadata().getUsage(), false);
                        KnowledgeAnswerOutput output = objectMapper.readValue(text, KnowledgeAnswerOutput.class);
                        return new LlmCallResult(output, usageInfo);
                } catch (RuntimeException | JsonProcessingException exception) {
                        log.warn(
                                        "QA structured output failed, fallback to raw content. groupId={}, evidenceCount={}",
                                        groupId,
                                        evidenceBundle.documents().size(),
                                        exception);
                        return parseFallbackAnswer(groupId, question, evidenceBundle);
                }
        }

        /**
         * 回退解析：当结构化输出失败时，调用大模型获取原始文本并用 {@link QaAnswerParser} 解析。
         */
        private LlmCallResult parseFallbackAnswer(
                        Long groupId,
                        String question,
                        RetrievedEvidenceBundle evidenceBundle) {
                Prompt userPrompt = createUserPrompt(question, evidenceBundle);
                try {
                        ChatResponse chatResponse = qaChatClient.prompt(userPrompt)
                                        .advisors(advisor -> advisor
                                                        .param("groupId", groupId)
                                                        .param(
                                                                        ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                                                        evidenceBundle.documents()))
                                        .call()
                                        .chatResponse();
                        String rawAnswer = chatResponse.getResult().getOutput().getText();
                        UsageInfo usageInfo = extractUsageInfo(chatResponse.getMetadata().getUsage(), false);
                        log.info(
                                        "QA raw answer fallback. groupId={}, evidenceCount={}, rawLength={}",
                                        groupId,
                                        evidenceBundle.documents().size(),
                                        rawAnswer == null ? 0 : rawAnswer.length());
                        KnowledgeAnswerOutput output = answerParser.parse(rawAnswer);
                        return new LlmCallResult(output, usageInfo);
                } catch (RuntimeException exception) {
                        log.error(
                                        "QA raw answer fallback failed. groupId={}, evidenceCount={}",
                                        groupId,
                                        evidenceBundle.documents().size(),
                                        exception);
                        return new LlmCallResult(null, new UsageInfo(0, 0, 0, false, 0L));
                }
        }

        private UsageInfo extractUsageInfo(Usage usage, boolean estimated) {
                if (usage == null) {
                        return new UsageInfo(0, 0, 0, estimated, 0L);
                }
                return new UsageInfo(
                                usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
                                usage.getTotalTokens() != null ? usage.getTotalTokens() : 0,
                                estimated,
                                0L);
        }

        /**
         * 流式问答上下文，封装 token 流和检索到的文档列表。
         * <p>
         * 用于 SSE 等流式传输场景：{@link #tokenStream} 提供大模型生成的 token 流，
         * {@link #documents} 用于在流式传输结束后组装引用来源。
         * </p>
         *
         * @param tokenStream 大模型生成的 token 流，每个元素为一个文本片段
         * @param documents   检索到的文档列表，用于后续组装引用来源
         */
        public record StreamContext(Flux<String> tokenStream, List<Document> documents) {
        }

        /**
         * 执行流式知识问答流程。
         * <p>
         * 与 {@link #ask(Long, String)} 流程一致：
         * 检索证据 → 若无证据则返回错误流 → 构造 Prompt → 调用大模型流式生成回答。
         * 区别在于大模型生成阶段使用流式调用，将回答以 token 流的形式返回。
         * </p>
         * <p>
         * 返回的 {@link StreamContext} 中：<br>
         * {@code tokenStream} — 大模型逐 token 输出的文本流；<br>
         * {@code documents} — 检索到的证据文档，供调用方在流结束后组装引用来源。
         * </p>
         *
         * @param groupId  群组 ID
         * @param question 用户问题
         * @return 流式问答上下文，包含 token 流和检索文档
         */
        public StreamContext askStream(Long groupId, String question) {
                return askStream(groupId, question, null);
        }

        /**
         * 执行流式知识问答流程，并在流完成后通过回调返回用量信息。
         *
         * @param groupId       群组 ID
         * @param question      用户问题
         * @param onUsageReady  流完成后回调用量信息（可能为 null）
         * @return 流式问答上下文，包含 token 流和检索文档
         */
        public StreamContext askStream(Long groupId, String question, Consumer<UsageInfo> onUsageReady) {
                long startNano = System.nanoTime();
                long startMs = System.currentTimeMillis();
                log.info("流式问答请求开始: groupId={}, questionLength={}", groupId, question != null ? question.length() : 0);

                RetrievedEvidenceBundle evidenceBundle = documentRetriever.retrieveEvidence(groupId, question);
                List<Document> documents = evidenceBundle.documents();
                log.info("流式问答证据检索完成: groupId={}, evidenceCount={}, evidenceLevel={}",
                                groupId, documents.size(), evidenceBundle.evidenceLevel());

                if (documents.isEmpty()) {
                        log.info("流式问答无证据可答: groupId={}, elapsedMs={}",
                                        groupId, (System.nanoTime() - startNano) / 1_000_000);
                        return new StreamContext(
                                        Flux.error(new BusinessException(
                                                        INSUFFICIENT_CODE + ": " + INSUFFICIENT_MESSAGE)),
                                        List.of());
                }

                Prompt userPrompt = createUserPrompt(question, evidenceBundle);

                AtomicReference<UsageInfo> usageRef = new AtomicReference<>();
                AtomicInteger charCount = new AtomicInteger(0);

                // 流式场景使用纯文本 System Prompt，覆盖默认的 JSON 输出要求
                // 使用 chatResponse() 获取 Flux<ChatResponse> 以便提取 usage
                Flux<String> tokenFlux = qaChatClient.prompt()
                                .system("你是群组知识问答助手，只能依据给定证据回答，不得补充外部知识或猜测。请直接输出纯文本回答正文，使用简体中文。不要输出 JSON、Markdown 等任何格式标记。")
                                .user(userPrompt.getContents())
                                .advisors(advisor -> advisor
                                                .param("groupId", groupId)
                                                .param(
                                                                ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                                                evidenceBundle.documents()))
                                .stream()
                                .chatResponse()
                                .map(response -> {
                                        Usage usage = response.getMetadata().getUsage();
                                        if (usage != null && usage.getTotalTokens() != null
                                                        && usage.getTotalTokens() > 0) {
                                                usageRef.set(extractUsageInfo(usage, false));
                                        }
                                        String text = null;
                                        if (response.getResult() != null && response.getResult().getOutput() != null) {
                                                text = response.getResult().getOutput().getText();
                                        }
                                        if (text != null) {
                                                charCount.addAndGet(text.length());
                                        }
                                        return text;
                                })
                                .filter(StringUtils::hasText)
                                .doOnComplete(() -> {
                                        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                                        long latencyMs = System.currentTimeMillis() - startMs;
                                        log.info("流式问答请求完成: groupId={}, elapsedMs={}", groupId, elapsedMs);

                                        UsageInfo usage = usageRef.get();
                                        if (usage == null) {
                                                int estimatedCompletion = charCount.get() / 4;
                                                usage = new UsageInfo(0, estimatedCompletion, estimatedCompletion, true,
                                                                latencyMs);
                                        } else {
                                                usage = new UsageInfo(usage.promptTokens(), usage.completionTokens(),
                                                                usage.totalTokens(), usage.estimated(), latencyMs);
                                        }
                                        if (onUsageReady != null) {
                                                onUsageReady.accept(usage);
                                        }
                                })
                                .doOnError(error -> log.error("流式问答异常: groupId={}", groupId, error));

                return new StreamContext(tokenFlux, documents);
        }

        /**
         * 构造用户提示词，将问题、证据等级和证据指导填充到模板中。
         */
        private Prompt createUserPrompt(String question, RetrievedEvidenceBundle evidenceBundle) {
                EvidenceLevel evidenceLevel = evidenceBundle.evidenceLevel() == null
                                ? EvidenceLevel.NONE
                                : evidenceBundle.evidenceLevel();
                return qaUserPromptTemplate.create(Map.of(
                                "question", question,
                                "evidenceLevel", evidenceLevel.name(),
                                "evidenceGuidance", evidenceBundle.evidenceGuidance()));
        }
}
