package com.acme.hr.leavetracker.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GoogleDecision(
        @NotNull UUID requestId,
        @NotBlank String action,
        @NotBlank String token,
        String managerComment
) { }
