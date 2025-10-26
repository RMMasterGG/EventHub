package com.semasem.service;

import com.semasem.dto.entity.TokenType;
import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.mapper.UserMapper;
import com.semasem.dto.request.*;
import com.semasem.dto.response.*;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.User;
import com.semasem.service.security.JwtService;
import com.semasem.service.security.PasswordEncoder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public RegisterResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encryptPassword(user.getPassword()));
        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);

        return new RegisterResponse(savedUser.getName(), savedUser.getEmail(), savedUser.getRole());
    }

    @Transactional
    public LoginResponse loginUser(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS, "Неверный email или пароль"));

        if (!passwordEncoder.checkPassword(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Неверный email или пароль");
        }

        String accessToken = jwtService.generateToken(user, TokenType.ACCESS_TOKEN);
        String refreshToken = jwtService.generateToken(user, TokenType.REFRESH_TOKEN);

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(refreshTokenCookie);

        return new LoginResponse(
                accessToken,
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public void logoutUser(HttpServletRequest request, HttpServletResponse response) {
        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    String refreshToken = cookie.getValue();
                    String email = jwtService.extractEmail(refreshToken);
                    User user = userRepository.findByEmail(email).orElse(null);
                    if (user != null) {
                        user.setRefreshToken(null);
                        userRepository.save(user);
                    }
                    break;
                }
            }
        }

        log.info("User logged out successfully");
    }

    @Transactional
    public RefreshTokenResponse refreshTokenForUser(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String refreshToken = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND, "Refresh token not found");
        }

        if (!jwtService.isTokenValid(refreshToken, TokenType.REFRESH_TOKEN)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "Invalid refresh token");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!refreshToken.equals(user.getRefreshToken())) {
            user.setRefreshToken(null);
            userRepository.save(user);
            throw new CustomException(ErrorCode.TOKEN_COMPROMISED, "Token compromised");
        }

        String newAccessToken = jwtService.generateToken(user, TokenType.ACCESS_TOKEN);
        String newRefreshToken = jwtService.generateToken(user, TokenType.REFRESH_TOKEN);

        user.setRefreshToken(newRefreshToken);
        userRepository.save(user);

        Cookie refreshTokenCookie = new Cookie("refreshToken", newRefreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(refreshTokenCookie);

        log.info("Tokens refreshed for user: {}", email);
        return new RefreshTokenResponse(newAccessToken);
    }

    public RecoveryPasswordResponse recoveryPassword(RecoveryPasswordRequest request, HttpServletRequest servletRequest) {
        String ipAddress = getClientIp(servletRequest);
        String email = request.getEmail();

        if (!userRepository.existsByEmail(email)) {
            log.warn("Password recovery attempt for non-existing email: {}", email);
            return new RecoveryPasswordResponse();
        }

        String token = java.util.UUID.randomUUID().toString().substring(0, 8);

        Map<String, String> variables = new HashMap<>();
        variables.put("token", token);
        variables.put("ip_address", ipAddress);
        variables.put("time", String.valueOf(LocalDate.now()));

        log.info("Password recovery code sent to: {}", email);
        return new RecoveryPasswordResponse();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String code) {
        String email = "user@example.com"; // Заглушка

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

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

        if (!passwordEncoder.checkPassword(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Неверный текущий пароль");
        }

        if (passwordEncoder.checkPassword(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Новый пароль должен отличаться от текущего");
        }

        user.setPassword(passwordEncoder.encryptPassword(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", email);
        return new NewPasswordResponse();
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