package com.semasem.service.security;

import com.semasem.dto.entity.TokenType;
import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.repository.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.semasem.dto.entity.TokenType.ACCESS_TOKEN;
import static com.semasem.dto.entity.TokenType.REFRESH_TOKEN;

@Slf4j
@Service
@SuppressWarnings("unused")
public class JwtService {

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.refresh-expiration-time:604800000}")
    private Long refreshExpirationTime;

    @Value("${jwt.access-expiration-time:900000}")
    private Long accessExpirationTime;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(User user, TokenType type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("name", user.getName());

        switch (type) {
            case REFRESH_TOKEN -> {
                claims.put("type", REFRESH_TOKEN);
                return Jwts.builder()
                        .claims(claims)
                        .subject(user.getEmail())
                        .issuedAt(new Date(System.currentTimeMillis()))
                        .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                        .signWith(getSigningKey())
                        .compact();
            }
            case ACCESS_TOKEN -> {
                claims.put("type", ACCESS_TOKEN);
                return Jwts.builder()
                        .claims(claims)
                        .subject(user.getEmail())
                        .issuedAt(new Date(System.currentTimeMillis()))
                        .expiration(new Date(System.currentTimeMillis() + accessExpirationTime))
                        .signWith(getSigningKey())
                        .compact();
            }
            default -> throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractName(String token) {
        return extractClaim(token, claims -> claims.get("name", String.class));
    }

    public String extractType(String token) {

        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public boolean isTokenValid(String token, TokenType type) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Проверяем тип токена, если указан
            if (type != null) {
                String tokenType = claims.get("type", String.class);
                if (!type.name().equals(tokenType)) {
                    log.warn("Invalid token type. Expected: {}, Actual: {}", type, tokenType);
                    return false;
                }
            }

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Invalid token format: {}", e.getMessage());
            return false;
        } catch (SecurityException e) {
            log.warn("Invalid signature: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Token is empty or null: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateDirectJoinToken(String roomId, String userEmail) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roomId", roomId);
        claims.put("tokenType", "DIRECT_JOIN");
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userEmail)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000)) // 5 минут
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateDirectJoinToken(String token, String expectedRoomId) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get("tokenType", String.class);
            String roomId = claims.get("roomId", String.class);

            return "DIRECT_JOIN".equals(tokenType) &&
                    expectedRoomId.equals(roomId) &&
                    !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String generateVisitorToken(String roomId, String inviteCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "VISITOR");
        claims.put("roomId", roomId);
        claims.put("inviteCode", inviteCode);
        claims.put("timestamp", System.currentTimeMillis());

        return Jwts.builder()
                .claims(claims)
                .subject("visitor")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 2 * 60 * 60 * 1000)) // 2 часа
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateVisitorToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get("type", String.class);
            return "VISITOR".equals(tokenType) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractRoomIdFromVisitorToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("roomId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

}
