package com.anjing.chat.controller;

import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.service.ChatService;
import com.anjing.model.response.APIResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天Controller
 * 
 * 提供聊天相关API
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ============== 会话管理 ==============

    /**
     * 创建会话（路径：POST /api/chat/conversations）
     */
    @PostMapping("/conversations")
    public APIResponse<ConversationResponse> createConversation(
            @RequestBody CreateConversationRequest request) {
        log.info("创建会话: title={}", request.getTitle());
        ConversationResponse response = chatService.createConversation(request);
        return APIResponse.success(response);
    }

    /**
     * 获取会话详情（路径：GET /api/chat/conversations/{id}）
     */
    @GetMapping("/conversations/{conversationId}")
    public APIResponse<ConversationResponse> getConversation(@PathVariable String conversationId) {
        ConversationResponse response = chatService.getConversation(conversationId);
        return APIResponse.success(response);
    }

    /**
     * 获取会话列表（路径：GET /api/chat/conversations）
     */
    @GetMapping("/conversations")
    public APIResponse<Map<String, Object>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ConversationResponse> pageResult = chatService.listConversations(page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("records", pageResult.getContent());
        data.put("total", pageResult.getTotalElements());
        data.put("currentPage", pageResult.getNumber() + 1);
        data.put("pageSize", pageResult.getSize());
        data.put("totalPage", pageResult.getTotalPages());
        return APIResponse.success(data);
    }

    /**
     * 删除会话（路径：DELETE /api/chat/conversations/{id}）
     */
    @DeleteMapping("/conversations/{conversationId}")
    public APIResponse<Void> deleteConversation(@PathVariable String conversationId) {
        log.info("删除会话: conversationId={}", conversationId);
        chatService.deleteConversation(conversationId);
        return APIResponse.success(null);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/conversations/{conversationId}/title")
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
    @PostMapping("/conversations/{conversationId}/messages")
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
    @GetMapping("/conversations/{conversationId}/messages")
    public APIResponse<List<MessageResponse>> getMessages(@PathVariable String conversationId) {
        List<MessageResponse> messages = chatService.getMessages(conversationId);
        return APIResponse.success(messages);
    }
}

