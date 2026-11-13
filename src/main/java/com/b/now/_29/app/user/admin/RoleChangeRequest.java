package com.b.now._29.app.user.admin;

import com.b.now._29.app.user.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role role) {}
