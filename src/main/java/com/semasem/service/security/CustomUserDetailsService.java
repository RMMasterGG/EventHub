package com.semasem.service.security;

import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Используем заглушку для пароля, так как аутентификация через JWT
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                "N/A", // заглушка для пароля
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}