package com.semasem.dto.mapper;

import com.semasem.dto.request.RegisterRequest;
import com.semasem.repository.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(request.getPassword());
        return user;
    }
}
