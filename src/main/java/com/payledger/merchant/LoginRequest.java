package com.payledger.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/login}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 72) String password) {
}
