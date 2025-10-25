package com.semasem.service;

import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.response.UserGetResponse;
import com.semasem.dto.response.UserProfileResponse;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.User;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserGetResponse getUser(Principal principal) {
        if (principal == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, "Пользователь не аутентифицирован");
        }

        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "Пользователь не найден"));

        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED, "Email не верифицирован");
        }

        return new UserGetResponse(
                user.getUuid().toString(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }


    public UserProfileResponse getProfile(Principal principal) {
        if (principal == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, "Пользователь не аутентифицирован");
        }

        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "Пользователь не найден"));

        return new UserProfileResponse(
                user.getUuid().toString(),
                user.getAvatarLink(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.isEmailVerified(),
                user.getRole()
        );
    }
}
