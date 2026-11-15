package com.b.now._29.app.user.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> getUsers() {
        return adminUserService.getUsers();
    }

    @GetMapping("/{id}")
    public AdminUserResponse getUser(@PathVariable long id) {
        return adminUserService.getUser(id);
    }

    @PutMapping("/{id}/role")
    public AdminUserResponse changeRole(@PathVariable long id, @Valid @RequestBody RoleChangeRequest request) {
        return adminUserService.changeRole(id, request.role(), request.categorySlugs());
    }
}
