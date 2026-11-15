package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserRoleService;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 요청한 관리자의 역할과 담당 카테고리로 관리 범위를 만든다. */
@Service
@Transactional(readOnly = true)
public class AdminScopeService {

    private final UserRoleService userRoleService;
    private final CategoryAdminRepository categoryAdminRepository;

    public AdminScopeService(UserRoleService userRoleService, CategoryAdminRepository categoryAdminRepository) {
        this.userRoleService = userRoleService;
        this.categoryAdminRepository = categoryAdminRepository;
    }

    public AdminScope of(long userId) {
        Role role = userRoleService.findRole(userId).orElse(Role.USER);
        if (role.isOwner()) {
            return AdminScope.owner();
        }
        if (role.requiresCategories()) {
            Set<Long> categoryIds = categoryAdminRepository.findAllByUserId(userId).stream()
                    .map(mapping -> mapping.getCategory().getId())
                    .collect(Collectors.toSet());
            return AdminScope.categories(categoryIds);
        }
        return AdminScope.categories(Set.of());
    }
}
