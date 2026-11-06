package com.b.now._29.app.quiz.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,50}") String slug,
        @NotBlank @Size(max = 100) String name,
        String description,
        int sortOrder) {}
