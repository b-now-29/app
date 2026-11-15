package com.b.now._29.app.user;

import com.b.now._29.app.quiz.admin.CategoryAdmin;
import com.b.now._29.app.quiz.admin.CategoryAdminRepository;
import com.b.now._29.app.quiz.category.CategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 테스트 계정 (일반, 운영자, 카테고리 관리자). 공개된 비밀번호라 dev 프로필에서만 만든다 (D9, 관리자 R9).
 *
 * <p>OWNER는 한 명뿐이므로 운영자 계정 (admin@example.com)은 OWNER가 없을 때만 만든다. 카테고리 관리자 계정 (manager@example.com)은 공개 카테고리
 * 목록의 첫 번째 카테고리를 담당한다.
 */
@Component
@Profile("dev")
public class TestAccountInitializer implements ApplicationRunner {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "test1234!";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "admin1234!";
    private static final String MANAGER_EMAIL = "manager@example.com";
    private static final String MANAGER_PASSWORD = "manager1234!";

    private final UserRepository userRepository;
    private final UserService userService;
    private final CategoryRepository categoryRepository;
    private final CategoryAdminRepository categoryAdminRepository;

    public TestAccountInitializer(
            UserRepository userRepository,
            UserService userService,
            CategoryRepository categoryRepository,
            CategoryAdminRepository categoryAdminRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.categoryRepository = categoryRepository;
        this.categoryAdminRepository = categoryAdminRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(EMAIL, PASSWORD, Role.USER, "테스트");
        if (!userRepository.existsByEmail(ADMIN_EMAIL) && !userRepository.existsByRole(Role.OWNER)) {
            userService.createAccount(ADMIN_EMAIL, ADMIN_PASSWORD, "관리자", Role.OWNER);
        }
        createManagerIfMissing();
    }

    private void createIfMissing(String email, String password, Role role, String nickname) {
        if (!userRepository.existsByEmail(email)) {
            userService.createAccount(email, password, nickname, role);
        }
    }

    /** 담당 카테고리가 하나도 없는 카테고리 관리자는 없으므로, 공개 카테고리가 없으면 만들지 않는다. */
    private void createManagerIfMissing() {
        if (userRepository.existsByEmail(MANAGER_EMAIL)) {
            return;
        }
        categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .findFirst()
                .ifPresent(first -> {
                    userService.createAccount(MANAGER_EMAIL, MANAGER_PASSWORD, "카테고리관리자", Role.CATEGORY_ADMIN);
                    User manager = userRepository.findByEmail(MANAGER_EMAIL).orElseThrow();
                    categoryAdminRepository.save(new CategoryAdmin(manager, first));
                });
    }
}
