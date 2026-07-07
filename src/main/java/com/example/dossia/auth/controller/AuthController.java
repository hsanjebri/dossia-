package com.example.dossia.auth.controller;

import com.example.dossia.auth.dto.LoginRequest;
import com.example.dossia.auth.dto.RegisterRequest;
import com.example.dossia.auth.dto.UserResponse;
import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.auth.service.AuthCookieService;
import com.example.dossia.auth.service.AuthService;
import com.example.dossia.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;

    public AuthController(
            AuthService authService, JwtService jwtService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        UserPrincipal principal = authService.register(request);
        authCookieService.setAuthCookie(response, jwtService.generateToken(principal));
        return authService.toResponse(principal);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        UserPrincipal principal = authService.login(request);
        authCookieService.setAuthCookie(response, jwtService.generateToken(principal));
        return authService.toResponse(principal);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authCookieService.clearAuthCookie(response);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.toResponse(principal);
    }
}
