package com.semasem.service.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordEncoder {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encryptPassword(String password) {
        return encoder.encode(password);
    }

    public boolean checkPassword(String password, String encryptedPassword) {
        return encoder.matches(password, encryptedPassword);
    }

}
