package com.semasem.controller;

import com.semasem.dto.request.CreateRoomRequest;
import com.semasem.dto.response.APIResponse;
import com.semasem.dto.response.ParticipantResponse;
import com.semasem.dto.response.RoomResponse;
import com.semasem.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@SuppressWarnings("unused")
@RequiredArgsConstructor
@Tag(
        name = "Room Management",
        description = "API для управления комнатами и взаимодействия с ними"
)
public class RoomController {

    private final RoomService roomService;

    @Operation(
            summary = "Создание новой комнаты",
            description = "Создает новую комнату для видеоконференций. " +
                    "Пользователь автоматически становится владельцем комнаты."
    )
    @PostMapping
    public ResponseEntity<APIResponse<RoomResponse>> createRoom(@RequestBody @Valid CreateRoomRequest request, Principal principal) {
        log.info("Create Room request: {}", request);

        RoomResponse response = roomService.createRoom(request, principal);

        log.debug("Create Room response {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.success("", response));
    }

    @Operation(
            summary = "Получение информации о комнате",
            description = "Возвращает детальную информацию о конкретной комнате по её ID."
    )
    @GetMapping("/{roomID}")
    public ResponseEntity<APIResponse<RoomResponse>> getRoom(@PathVariable UUID roomID, Principal principal) {

        RoomResponse response = roomService.getRoom(roomID, principal);

        log.debug("Get Room response {}", response);
        return ResponseEntity.ok().body(APIResponse.success("", response));
    }

    @Operation(
            summary = "Получение списка комнат пользователя",
            description = "Возвращает все комнаты, в которых пользователь является участником или владельцем."
    )
    @GetMapping("/my-rooms")
    public ResponseEntity<APIResponse<List<RoomResponse>>> getUserRooms(Principal principal) {

        List<RoomResponse> response = roomService.getUserRooms(principal);

        log.debug("Get User Room response {}", response);
        return ResponseEntity.ok().body(APIResponse.success("", response));
    }

    @Operation(
            summary = "Удаление комнаты",
            description = "Удаляет комнату. Только владелец комнаты может её удалить."
    )
    @DeleteMapping("/{roomID}")
    public ResponseEntity<APIResponse<RoomResponse>> deleteRoom(@PathVariable UUID roomID, Principal principal) {

        RoomResponse response = roomService.deleteRoom(roomID, principal);

        log.debug("Delete Room response {}", response);
        return ResponseEntity.ok().body(APIResponse.success("", response));
    }

    @Operation(
            summary = "Присоединение к комнате",
            description = "Присоединяет пользователя к комнате для участия в видеоконференции."
    )
    @PostMapping("/{roomID}/join")
    public ResponseEntity<APIResponse<RoomResponse>> joinRoom(@PathVariable UUID roomID, Principal principal) {
        RoomResponse response = roomService.joinRoom(roomID, principal);
        return ResponseEntity.ok().body(APIResponse.success("Successfully joined the room", response));
    }

    @Operation(
            summary = "Выход из комнаты",
            description = "Покидает комнату и завершает участие в видеоконференции."
    )
    @PostMapping("/{roomID}/leave")
    public ResponseEntity<APIResponse<Void>> leaveRoom(@PathVariable UUID roomID, Principal principal) {
        roomService.leaveRoom(roomID, principal);
        return ResponseEntity.ok().body(APIResponse.success("Successfully left the room"));
    }

    @Operation(
            summary = "Получить участников комнаты",
            description = "Возвращает список активных участников комнаты."
    )
    @GetMapping("/{roomID}/participants")
    public ResponseEntity<APIResponse<List<ParticipantResponse>>> getRoomParticipants(@PathVariable UUID roomID, Principal principal) {
        List<ParticipantResponse> response = roomService.getRoomParticipants(roomID, principal);
        return ResponseEntity.ok().body(APIResponse.success("", response));
    }
}

