package com.example.dossia.auth.service;

import com.example.dossia.auth.domain.User;
import com.example.dossia.auth.domain.UserRole;
import com.example.dossia.auth.dto.LoginRequest;
import com.example.dossia.auth.dto.RegisterRequest;
import com.example.dossia.auth.dto.UserResponse;
import com.example.dossia.auth.repository.UserRepository;
import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.common.ConflictException;
import com.example.dossia.common.UnauthorizedException;
import com.example.dossia.config.AuthProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Transactional
    public UserPrincipal register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(authProperties.isAdminEmail(email) ? UserRole.ADMIN : UserRole.CITIZEN);

        return new UserPrincipal(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserPrincipal login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return new UserPrincipal(user);
    }

    public UserResponse toResponse(UserPrincipal principal) {
        return new UserResponse(
                principal.getId(),
                principal.getName(),
                principal.getUsername(),
                principal.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
