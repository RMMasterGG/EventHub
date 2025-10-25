package com.semasem.dto.response;

import com.semasem.repository.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserGetResponse {
    private String userId;
    private String name;
    private String email;
    private UserRole role;
}
