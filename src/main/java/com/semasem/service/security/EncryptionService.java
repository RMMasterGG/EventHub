package com.semasem.service.security;

import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    public String generateRoomKey() {
        // Генерация AES ключа для комнаты

        return "";
    }

    public String encryptMessage(String content, String key) {
        // Шифрование сообщения

        return "";
    }

    public String decryptMessage(String encryptedContent, String key) {
        // Дешифровка сообщения

        return "";
    }
}
