package com.semasem.controller;

import com.semasem.dto.request.RegisterRequest;
import com.semasem.dto.response.APIResponse;
import com.semasem.dto.response.RegisterResponse;
import com.semasem.dto.response.UserGetResponse;
import com.semasem.dto.response.UserProfileResponse;
import com.semasem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping(path = "api/user")
@SuppressWarnings("unused")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "User API",
        description = "API для взаимодействия с данными пользователя."
)
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Информация о пользователе",
            description = "Возвращает информацию о пользователе необходимую для работы сервиса."
    )
    @GetMapping("/me")
    public ResponseEntity<APIResponse<UserGetResponse>> getUser(Principal principal) {

        UserGetResponse response = userService.getUser(principal);

        log.info("Register response: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.success("User Created!", response));
    }


    @Operation(
            summary = "Профиль пользователя",
            description = "Возвращает данные необходимые для профиля пользователя."
    )
    @GetMapping("/profile")
    public ResponseEntity<APIResponse<UserProfileResponse>> getUserProfile(Principal principal) {

        UserProfileResponse response = userService.getProfile(principal);

        log.info("Profile response: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.success("User Created!", response));
    }
}
