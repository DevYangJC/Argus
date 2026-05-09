package com.argus.rag.qa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 知识问答请求 DTO。
 * <p>
 * 客户端提交问题时的入参，包含目标群组 ID 和用户问题文本。
 * 系统会在指定群组的知识库范围内检索并回答问题。
 * </p>
 */
public class AskQuestionRequest {

    /** 目标群组 ID，必填，必须为正整数，用于限定知识库检索范围 */
    @NotNull(message = "groupId 不能为空")
    @Positive(message = "groupId 非法")
    private Long groupId;

    /** 用户问题文本，必填，不能为空白，最大长度 2000 个字符 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过 2000")
    private String question;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
