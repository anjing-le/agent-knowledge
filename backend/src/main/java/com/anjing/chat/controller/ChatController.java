package com.anjing.chat.controller;

import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.service.ChatService;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import com.anjing.model.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天Controller
 * 
 * 提供聊天相关API
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.Chat.BASE)
@RequiredArgsConstructor
@Tag(name = "RAG Chat", description = "RAG 会话、消息和答案引用接口")
public class ChatController {

    private final ChatService chatService;

    // ============== 会话管理 ==============

    /**
     * 创建会话（路径：POST /api/chat/conversations）
     */
    @PostMapping(ApiConstants.Chat.CONVERSATIONS)
    @Operation(summary = "创建会话")
    public APIResponse<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        log.info("创建会话: title={}", request.getTitle());
        ConversationResponse response = chatService.createConversation(request);
        return APIResponse.success(response);
    }

    /**
     * 获取会话详情（路径：GET /api/chat/conversations/{id}）
     */
    @GetMapping(ApiConstants.Chat.CONVERSATION_DETAIL)
    @Operation(summary = "获取会话详情")
    public APIResponse<ConversationResponse> getConversation(@PathVariable String conversationId) {
        ConversationResponse response = chatService.getConversation(conversationId);
        return APIResponse.success(response);
    }

    /**
     * 获取会话列表（路径：GET /api/chat/conversations）
     */
    @GetMapping(ApiConstants.Chat.CONVERSATIONS)
    @Operation(summary = "分页查询会话")
    public APIResponse<PageResult<ConversationResponse>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ConversationResponse> pageResult = chatService.listConversations(page, size);
        return APIResponse.success(PageResult.of(
                pageResult.getContent(),
                pageResult.getTotalElements(),
                pageResult.getNumber() + 1,
                pageResult.getSize()
        ));
    }

    /**
     * 删除会话（路径：DELETE /api/chat/conversations/{id}）
     */
    @DeleteMapping(ApiConstants.Chat.CONVERSATION_DETAIL)
    @Operation(summary = "删除会话")
    public APIResponse<Void> deleteConversation(@PathVariable String conversationId) {
        log.info("删除会话: conversationId={}", conversationId);
        chatService.deleteConversation(conversationId);
        return APIResponse.success();
    }

    /**
     * 更新会话标题
     */
    @PutMapping(ApiConstants.Chat.CONVERSATION_TITLE)
    @Operation(summary = "更新会话标题")
    public APIResponse<ConversationResponse> updateConversationTitle(
            @PathVariable String conversationId,
            @RequestParam String title) {
        log.info("更新会话标题: conversationId={}, title={}", conversationId, title);
        ConversationResponse response = chatService.updateConversationTitle(conversationId, title);
        return APIResponse.success(response);
    }

    // ============== 消息管理 ==============

    /**
     * 发送消息（路径：POST /api/chat/conversations/{id}/messages）
     */
    @PostMapping(ApiConstants.Chat.MESSAGES)
    @Operation(summary = "发送消息")
    public APIResponse<MessageResponse> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        request.setConversationId(conversationId);
        log.info("发送消息: conversationId={}, contentLength={}", 
                conversationId, request.getContent().length());
        MessageResponse response = chatService.sendMessage(request);
        return APIResponse.success(response);
    }

    /**
     * 获取会话消息历史（路径：GET /api/chat/conversations/{id}/messages）
     */
    @GetMapping(ApiConstants.Chat.MESSAGES)
    @Operation(summary = "获取会话消息历史")
    public APIResponse<List<MessageResponse>> getMessages(@PathVariable String conversationId) {
        List<MessageResponse> messages = chatService.getMessages(conversationId);
        return APIResponse.success(messages);
    }
}
