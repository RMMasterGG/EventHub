package com.semasem.service;

import com.semasem.dto.entity.TokenType;
import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.mapper.UserMapper;
import com.semasem.dto.request.*;
import com.semasem.dto.response.*;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.User;
import com.semasem.service.security.EmailCodeService;
import com.semasem.service.security.EmailService;
import com.semasem.service.security.JwtService;
import com.semasem.service.security.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
@SuppressWarnings("unused")
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailCodeService emailCodeService;

    public RegisterResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encryptPassword(user.getPassword()));
        User savedUser = userRepository.save(user);

        String code = emailCodeService.generateCode(6);
        emailCodeService.saveCode(savedUser.getEmail(), code, 10);

        String template = emailService.processTemplate("confirmation.html", code);
        emailService.sendEmail(template, savedUser.getEmail());

        return new RegisterResponse(savedUser.getName(), savedUser.getEmail(), savedUser.getRole());
    }

    @Transactional
    public VerifyEmailResponse verifyEmail(String code) {
        String email = emailCodeService.getEmailByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_VERIFICATION_CODE, "Код не найден!"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new CustomException(ErrorCode.ALREADY_VERIFIED, "Пользователь уже верифицирован!");
        }

        if (!emailCodeService.validCode(email, code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE, "Неверный код верификации");
        }

        String refreshToken = jwtService.generateToken(user, TokenType.REFRESH_TOKEN);
        String accessToken = jwtService.generateToken(user, TokenType.ACCESS_TOKEN);

        user.setRefreshToken(refreshToken);
        user.setEmailVerified(true);
        userRepository.save(user);

        return new VerifyEmailResponse(
                refreshToken,
                accessToken,
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public LoginResponse loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS, "Неверный email или пароль"));

        if (!passwordEncoder.checkPassword(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Неверный email или пароль");
        }

        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED, "Email не верифицирован");
        }

        String refreshToken = jwtService.generateToken(user, TokenType.REFRESH_TOKEN);
        String accessToken = jwtService.generateToken(user, TokenType.ACCESS_TOKEN);

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return new LoginResponse(
                refreshToken,
                accessToken,
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public void logoutUser(HttpServletRequest servletRequest) {
        String header = servletRequest.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND, "Токен не найден");
        }

        String token = header.substring(7);

        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.setRefreshToken(null);
        userRepository.save(user);

        // Логика добавления токена в чёрный список
        log.info("User logged out: {}", email);
    }

    public RecoveryPasswordResponse recoveryPassword(RecoveryPasswordRequest request, HttpServletRequest servletRequest) {
        String ipAddress = getClientIp(servletRequest);
        String email = request.getEmail();

        if (!userRepository.existsByEmail(email)) {
            log.warn("Password recovery attempt for non-existing email: {}", email);
            return new RecoveryPasswordResponse(); // Всегда возвращаем успех для безопасности
        }

        String token = emailCodeService.generateCode(8);
        emailCodeService.saveCode(email, token, 15);

        Map<String, String> variables = new HashMap<>();
        variables.put("token", token);
        variables.put("ip_address", ipAddress);
        variables.put("time", String.valueOf(LocalDate.now()));

        String template = emailService.processTemplate("recovery.html", variables);
        emailService.sendEmail(template, email);

        log.info("Password recovery code sent to: {}", email);
        return new RecoveryPasswordResponse();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String code) {
        String email = emailCodeService.getEmailByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_VERIFICATION_CODE, "Код не найден!"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!emailCodeService.validCode(email, code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE, "Неверный код восстановления");
        }

        if (passwordEncoder.checkPassword(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Новый пароль должен отличаться от старого");
        }

        user.setPassword(passwordEncoder.encryptPassword(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", email);
        return new ResetPasswordResponse();
    }

    @Transactional
    public NewPasswordResponse newPasswordUser(NewPasswordRequest request, HttpServletRequest servletRequest) {
        String header = servletRequest.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND, "Токен не найден");
        }

        String accessToken = header.substring(7);

        if (!jwtService.isTokenValid(accessToken, TokenType.ACCESS_TOKEN)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "Невалидный access token");
        }

        String email = jwtService.extractEmail(accessToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Проверяем текущий пароль
        if (!passwordEncoder.checkPassword(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Неверный текущий пароль");
        }

        // Проверяем, что новый пароль отличается от старого
        if (passwordEncoder.checkPassword(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Новый пароль должен отличаться от текущего");
        }

        // Устанавливаем новый пароль
        user.setPassword(passwordEncoder.encryptPassword(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", email);
        return new NewPasswordResponse();
    }

    @Transactional
    public RefreshTokenResponse refreshTokenForUser(HttpServletRequest servletRequest) {
        String header = servletRequest.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND, "Токен не найден");
        }

        String refreshToken = header.substring(7);

        if (!jwtService.isTokenValid(refreshToken, TokenType.REFRESH_TOKEN)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "Невалидный refresh token");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!refreshToken.equals(user.getRefreshToken())) {
            // Токен скомпрометирован - очищаем
            user.setRefreshToken(null);
            userRepository.save(user);
            throw new CustomException(ErrorCode.TOKEN_COMPROMISED, "Токен скомпрометирован");
        }

        String newAccessToken = jwtService.generateToken(user, TokenType.ACCESS_TOKEN);
        String newRefreshToken = jwtService.generateToken(user, TokenType.REFRESH_TOKEN);

        user.setRefreshToken(newRefreshToken);
        userRepository.save(user);

        log.info("Tokens refreshed for user: {}", email);
        return new RefreshTokenResponse(newAccessToken, newRefreshToken);
    }

    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");

        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress != null ? ipAddress : "unknown";
    }
}