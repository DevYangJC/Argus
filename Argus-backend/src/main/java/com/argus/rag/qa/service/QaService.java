package com.dong.ddrag.qa.service;

import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.qa.model.dto.AskQuestionRequest;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class QaService {

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;

    public QaService(
            GroupMembershipService groupMembershipService,
            QaChatService qaChatService
    ) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
    }

    public AskQuestionResponse ask(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(request, groupId);
        return qaChatService.ask(groupId, askQuestionRequest.getQuestion());
    }
}
