package com.semasem.controller;

import com.semasem.dto.request.ChatMessageRequest;
import com.semasem.dto.request.EditMessageRequest;
import com.semasem.dto.response.APIResponse;
import com.semasem.dto.response.ChatMessageResponse;
import com.semasem.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/rooms/{roomId}/chat")
@RequiredArgsConstructor
@Tag(name = "Chat Management", description = "API для управления историей чата")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Получить историю сообщений", description = "Возвращает историю сообщений комнаты с пагинацией")
    @GetMapping("/messages")
    public ResponseEntity<APIResponse<Page<ChatMessageResponse>>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Principal principal) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<ChatMessageResponse> response = chatService.getRoomMessages(roomId, pageable, principal);

        return ResponseEntity.ok(APIResponse.success("", response));
    }


    @Operation(summary = "Получить последние сообщения", description = "Возвращает последние 50 сообщений комнаты")
    @GetMapping("/messages/recent")
    public ResponseEntity<APIResponse<List<ChatMessageResponse>>> getRecentMessages(
            @PathVariable UUID roomId,
            Principal principal) {

        List<ChatMessageResponse> response = chatService.getRecentMessages(roomId, principal);
        return ResponseEntity.ok(APIResponse.success("", response));
    }

    @Operation(summary = "Получить новые сообщения", description = "Возвращает сообщения после указанной даты")
    @GetMapping("/messages/new")
    public ResponseEntity<APIResponse<List<ChatMessageResponse>>> getNewMessages(
            @PathVariable UUID roomId,
            @RequestParam Instant after,
            Principal principal) {

        List<ChatMessageResponse> response = chatService.getMessagesAfter(roomId, after, principal);
        return ResponseEntity.ok(APIResponse.success("", response));
    }

    @Operation(summary = "Редактировать сообщение", description = "Редактирует существующее сообщение")
    @PutMapping("/messages/{messageId}")
    public ResponseEntity<APIResponse<ChatMessageResponse>> editMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            @RequestBody EditMessageRequest request,
            Principal principal) {

        ChatMessageResponse response = chatService.editMessage(roomId, messageId, request, principal);
        return ResponseEntity.ok(APIResponse.success("Message updated", response));
    }

    @Operation(summary = "Удалить сообщение", description = "Удаляет сообщение из чата")
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<APIResponse<Void>> deleteMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            Principal principal) {

        chatService.deleteMessage(roomId, messageId, principal);
        return ResponseEntity.ok(APIResponse.success("Message deleted"));
    }

    @Operation(summary = "Отправить сообщение", description = "Отправляет новое сообщение в чат (REST endpoint)")
    @PostMapping("/messages")
    public ResponseEntity<APIResponse<ChatMessageResponse>> sendMessage(
            @PathVariable UUID roomId,
            @RequestBody ChatMessageRequest request,
            Principal principal) {

        ChatMessageResponse response = chatService.sendMessage(roomId, request, principal);
        return ResponseEntity.ok(APIResponse.success("Message sent", response));
    }
}
