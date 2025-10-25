package com.semasem.service;

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

    public VerifyEmailResponse verifyEmail(String code) {

        String email = emailCodeService.getEmailByCode(code).orElseThrow(() -> new CustomException(ErrorCode.INVALID_VERIFICATION_CODE, "Код не найден!"));

        User user = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "Пользователь уже верифицирован!");

        if (!emailCodeService.validCode(email, code)) throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);

        user.setEmailVerified(true);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return new VerifyEmailResponse(
                token,
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    public LoginResponse loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.checkPassword(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isEmailVerified()) throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    public void logoutUser(HttpServletRequest servletRequest) {
        String header = servletRequest.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) throw new CustomException(ErrorCode.OBJECT_NOT_FOUND, "Токен не найден");

        String token = header.substring(7);
        // Логика добавления токена в чёрный список

        log.debug("Token add in blacklist: {}", token);
    }

    public RecoveryPasswordResponse recoveryPassword(RecoveryPasswordRequest request, HttpServletRequest servletRequest) {
        String ip_address = getClientIp(servletRequest);

        String email = request.getEmail();

        String token = emailCodeService.generateCode(8);

        emailCodeService.saveCode(email, token, 15);

        Map<String, String> variables = new HashMap<>();
        variables.put("token", token);
        variables.put("ip_address", ip_address);
        variables.put("time", String.valueOf(LocalDate.now()));

        String template = emailService.processTemplate("recovery.html", variables);

        emailService.sendEmail(template, email);

        return new RecoveryPasswordResponse();
    }

    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String code) {
        String email = emailCodeService.getEmailByCode(code).orElseThrow(() -> new CustomException(ErrorCode.INVALID_VERIFICATION_CODE, "Код не найден!"));



        return new ResetPasswordResponse();
    }

    public NewPasswordResponse newPasswordUser(NewPasswordRequest request, HttpServletRequest servletRequest) {




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
