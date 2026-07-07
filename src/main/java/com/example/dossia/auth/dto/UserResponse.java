package com.example.dossia.auth.dto;

import com.example.dossia.auth.domain.UserRole;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, UserRole role) {}
