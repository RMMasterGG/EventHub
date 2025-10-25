package com.semasem.controller;

import com.semasem.dto.request.*;
import com.semasem.dto.response.*;
import com.semasem.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping(path = "api/auth")
@SuppressWarnings("unused")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "Authentication API",
        description = "API для управления аутентификацией и регистрацией."
)
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Регистрация пользователя",
            description = "Создает нового пользователя."
    )
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<APIResponse<RegisterResponse>> registerUser(@RequestBody @Valid RegisterRequest request) {
        log.debug("Register request: {}", request);

        RegisterResponse response = authService.registerUser(request);

        log.info("Register response: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.success("User Created!", response));
    }

    @Operation(
            summary = "Вход в аккаунт",
            description = "Аутентифицирует пользователя и возвращает access token."
    )
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<APIResponse<LoginResponse>> loginUser(@RequestBody LoginRequest request, HttpServletResponse response) {
        log.debug("Login request: {}", request);

        LoginResponse loginResponse = authService.loginUser(request, response);

        log.info("Login response: {}", loginResponse);
        return ResponseEntity.ok().body(APIResponse.success("Success login!", loginResponse));
    }

    @Operation(
            summary = "Выход из системы",
            description = "Завершает сеанс пользователя и очищает refresh token."
    )
    @PostMapping("/logout")
    public ResponseEntity<APIResponse<Void>> logoutUser(HttpServletRequest request, HttpServletResponse response) {

        authService.logoutUser(request, response);

        log.info("Logged out successfully!");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(APIResponse.success("Logged out successfully!"));
    }

    @Operation(
            summary = "Обновление access token",
            description = "Обновляет access token с помощью refresh token из куки."
    )
    @PostMapping("/refresh")
    public ResponseEntity<APIResponse<RefreshTokenResponse>> refreshTokenForUser(HttpServletRequest request, HttpServletResponse response) {

        RefreshTokenResponse refreshResponse = authService.refreshTokenForUser(request, response);

        log.info("Refresh response: {}", refreshResponse);
        return ResponseEntity.ok().body(APIResponse.success("New access token", refreshResponse));
    }

    @Operation(
            summary = "Запрос восстановления пароля",
            description = "Инициирует процесс восстановления пароля."
    )
    @SecurityRequirements
    @PostMapping("/recovery-password")
    public ResponseEntity<APIResponse<RecoveryPasswordResponse>> recoverPassword(@RequestBody RecoveryPasswordRequest request,
                                                                                 HttpServletRequest servletRequest) {
        log.debug("Recovery-Password request: {}", request);

        RecoveryPasswordResponse response = authService.recoveryPassword(request, servletRequest);

        log.info("Recovery-Password response: {}", response);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(APIResponse.success("Email for to refresh password sending", response));
    }

    @Operation(
            summary = "Сброс пароля",
            description = "Сбрасывает пароль пользователя по коду восстановления."
    )
    @SecurityRequirements
    @GetMapping("/reset-password")
    public ResponseEntity<APIResponse<ResetPasswordResponse>> resetPassword(@RequestBody ResetPasswordRequest request, String code) {
        log.debug("Reset-Password request: {}", request);

        ResetPasswordResponse response = authService.resetPassword(request, code);

        log.info("Reset-Password response: {}", response);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(APIResponse.success("Reset password successfully"));
    }

    @Operation(
            summary = "Установка нового пароля",
            description = "Устанавливает новый пароль для аутентифицированного пользователя."
    )
    @PostMapping("/new-password")
    public ResponseEntity<APIResponse<NewPasswordResponse>> newPassword(@RequestBody NewPasswordRequest request, HttpServletRequest servletRequest) {
        log.debug("New-Password request: {}", request);

        NewPasswordResponse response = authService.newPasswordUser(request, servletRequest);

        log.info("New-Password response: {}", response);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(APIResponse.success("New password successfully!"));
    }
}