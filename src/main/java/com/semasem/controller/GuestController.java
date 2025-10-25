package com.semasem.controller;

import com.semasem.dto.request.GuestJoinRequest;
import com.semasem.dto.response.APIResponse;
import com.semasem.dto.response.GuestJoinResponse;
import com.semasem.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
@Tag(name = "Guest Access", description = "API для гостевого доступа к комнатам")
public class GuestController {

    private final GuestService guestService;

    @Operation(
            summary = "Присоединиться как гость",
            description = "Создает временного гостевого пользователя и присоединяет к комнате"
    )
    @SecurityRequirements
    @PostMapping("/join")
    public ResponseEntity<APIResponse<GuestJoinResponse>> joinAsGuest(
            @RequestBody @Valid GuestJoinRequest request) {

        GuestJoinResponse response = guestService.joinAsGuest(request);
        return ResponseEntity.ok(APIResponse.success("Guest joined successfully", response));
    }
}
