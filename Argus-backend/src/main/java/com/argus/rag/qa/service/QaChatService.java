package com.argus.rag.qa.service;

import com.argus.rag.qa.model.EvidenceLevel;
import com.argus.rag.qa.model.KnowledgeAnswerOutput;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.rag.ReadyChunkDocumentRetriever;
import com.argus.rag.qa.rag.RetrievedEvidenceBundle;
import com.argus.rag.qa.support.CitationAssembler;
import com.argus.rag.qa.support.QaAnswerParser;
import com.argus.rag.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

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

        private final ChatClient qaChatClient;
        private final PromptTemplate qaUserPromptTemplate;
        private final ReadyChunkDocumentRetriever documentRetriever;
        private final QaAnswerParser answerParser;
        private final CitationAssembler citationAssembler;

        /**
         * 构造函数。
         *
         * @param qaChatClient         问答专用的 ChatClient
         * @param qaUserPromptTemplate 用户提示词模板
         * @param documentRetriever    文档检索器
         * @param answerParser         回答解析器（用于回退解析）
         * @param citationAssembler    引用组装器
         */
        public QaChatService(
                        ChatClient qaChatClient,
                        @Qualifier("qaUserPromptTemplate") PromptTemplate qaUserPromptTemplate,
                        ReadyChunkDocumentRetriever documentRetriever,
                        QaAnswerParser answerParser,
                        CitationAssembler citationAssembler) {
                this.qaChatClient = qaChatClient;
                this.qaUserPromptTemplate = qaUserPromptTemplate;
                this.documentRetriever = documentRetriever;
                this.answerParser = answerParser;
                this.citationAssembler = citationAssembler;
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
        public AskQuestionResponse ask(Long groupId, String question) {
                long startNano = System.nanoTime();
                log.info("问答请求开始: groupId={}, questionLength={}", groupId, question != null ? question.length() : 0);
                RetrievedEvidenceBundle evidenceBundle = documentRetriever.retrieveEvidence(groupId, question);
                List<Document> documents = evidenceBundle.documents();
                log.info("证据检索完成: groupId={}, evidenceCount={}, evidenceLevel={}",
                                groupId, documents.size(), evidenceBundle.evidenceLevel());
                if (documents.isEmpty()) {
                        log.info("问答无证据可答: groupId={}, elapsedMs={}",
                                        groupId, (System.nanoTime() - startNano) / 1_000_000);
                        return AskQuestionResponse.unanswered(INSUFFICIENT_CODE, INSUFFICIENT_MESSAGE, List.of());
                }
                KnowledgeAnswerOutput output = getStructuredAnswer(groupId, question, evidenceBundle);
                if (output == null) {
                        log.warn("问答结构化输出失败: groupId={}, evidenceCount={}", groupId, documents.size());
                        return AskQuestionResponse.unanswered(FORMAT_ERROR_CODE, FORMAT_ERROR_MESSAGE, List.of());
                }
                if (!output.answered() || !StringUtils.hasText(output.answer())) {
                        log.info("模型拒答: groupId={}, reasonCode={}, reasonMessage={}",
                                        groupId, output.reasonCode(), output.reasonMessage());
                        return AskQuestionResponse.unanswered(output.reasonCode(), output.reasonMessage(), List.of());
                }
                long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                log.info("问答请求完成: groupId={}, answerLength={}, citationCount={}, elapsedMs={}",
                                groupId, output.answer().length(), documents.size(), elapsedMs);
                return AskQuestionResponse.answered(
                                output.answer().trim(),
                                citationAssembler.assembleDocuments(documents));
        }

        /**
         * 调用大模型获取结构化回答。
         * <p>
         * 尝试使用 {@code entity()} 方式解析结构化输出，
         * 失败时回退为原文解析方式。
         * </p>
         */
        private KnowledgeAnswerOutput getStructuredAnswer(
                        Long groupId,
                        String question,
                        RetrievedEvidenceBundle evidenceBundle) {
                Prompt userPrompt = createUserPrompt(question, evidenceBundle);
                try {
                        return qaChatClient.prompt(userPrompt)
                                        .advisors(advisor -> advisor
                                                        .param("groupId", groupId)
                                                        .param(
                                                                        ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                                                        evidenceBundle.documents()))
                                        .call()
                                        .entity(KnowledgeAnswerOutput.class);
                } catch (RuntimeException exception) {
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
        private KnowledgeAnswerOutput parseFallbackAnswer(
                        Long groupId,
                        String question,
                        RetrievedEvidenceBundle evidenceBundle) {
                Prompt userPrompt = createUserPrompt(question, evidenceBundle);
                try {
                        String rawAnswer = qaChatClient.prompt(userPrompt)
                                        .advisors(advisor -> advisor
                                                        .param("groupId", groupId)
                                                        .param(
                                                                        ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                                                        evidenceBundle.documents()))
                                        .call()
                                        .content();
                        log.info(
                                        "QA raw answer fallback. groupId={}, evidenceCount={}, rawLength={}",
                                        groupId,
                                        evidenceBundle.documents().size(),
                                        rawAnswer == null ? 0 : rawAnswer.length());
                        return answerParser.parse(rawAnswer);
                } catch (RuntimeException exception) {
                        log.error(
                                        "QA raw answer fallback failed. groupId={}, evidenceCount={}",
                                        groupId,
                                        evidenceBundle.documents().size(),
                                        exception);
                        return null;
                }
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
                long startNano = System.nanoTime();
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

                // 流式场景使用纯文本 System Prompt，覆盖默认的 JSON 输出要求
                Flux<String> tokenFlux = qaChatClient.prompt()
                                .system("你是群组知识问答助手，只能依据给定证据回答，不得补充外部知识或猜测。请直接输出纯文本回答正文，使用简体中文。不要输出 JSON、Markdown 等任何格式标记。")
                                .user(userPrompt.getContents())
                                .advisors(advisor -> advisor
                                                .param("groupId", groupId)
                                                .param(
                                                                ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                                                evidenceBundle.documents()))
                                .stream()
                                .content()
                                .doOnComplete(() -> {
                                        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                                        log.info("流式问答请求完成: groupId={}, elapsedMs={}", groupId, elapsedMs);
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
