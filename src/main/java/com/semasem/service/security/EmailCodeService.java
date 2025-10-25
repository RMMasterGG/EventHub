package com.semasem.service.security;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailCodeService {
    private static final String PATERN = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CodeData> codeStorage = new ConcurrentHashMap<>();
    private final Map<String, String> codeToEmailStorage = new ConcurrentHashMap<>();

    public void saveCode(String email, String code, int timeMinutes) {
        LocalDateTime expirationTime  = LocalDateTime.now().plusMinutes(timeMinutes);
        CodeData codeData = new CodeData(code, expirationTime);
        if (codeToEmailStorage.containsKey(code)) {
            String oldCode = codeStorage.get(email).code;
            codeToEmailStorage.remove(oldCode);
        }
        codeStorage.put(email, codeData);
        codeToEmailStorage.put(code, email);
    }

    public boolean validCode(String email, String code) {
        if (!codeStorage.containsKey(email)) return false;
        CodeData codeData = codeStorage.get(email);
        if (LocalDateTime.now().isAfter(codeData.expirationTime)) {
            codeStorage.remove(email);
            codeToEmailStorage.remove(code);
            return false;
        }
        boolean isValid = codeData.code.equals(code);
        if (isValid) {
            codeStorage.remove(email);
            codeToEmailStorage.remove(code);
        }
        return isValid;
    }

    public Optional<String> getEmailByCode(String code) {
        if (!codeToEmailStorage.containsKey(code)) {
            return Optional.empty();
        }

        String email = codeToEmailStorage.get(code);

        if (codeStorage.containsKey(email)) {
            if (LocalDateTime.now().isAfter(codeStorage.get(email).expirationTime)) {
                codeStorage.remove(email);
                codeToEmailStorage.remove(code);
                return Optional.empty();
            }
        }

        return Optional.of(email);
    }

    public void cleanExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        codeStorage.entrySet().removeIf(entry ->
                now.isAfter(entry.getValue().expirationTime)
        );
    }

    private record CodeData(String code, LocalDateTime expirationTime) {

    }

    public String generateCode(int size) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < size; i++) {
            int index = secureRandom.nextInt(PATERN.length());
            code.append(PATERN.charAt(index));
        }
        return code.toString();
    }
}
