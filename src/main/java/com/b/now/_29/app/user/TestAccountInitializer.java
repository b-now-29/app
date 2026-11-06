package com.b.now._29.app.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 로컬 개발용 테스트 계정 (일반, 관리자). 공개된 비밀번호라 dev 프로필에서만 만든다 (D9, 관리자 R9). */
@Component
@Profile("dev")
public class TestAccountInitializer implements ApplicationRunner {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "test1234!";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "admin1234!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(EMAIL, PASSWORD, Role.USER);
        createIfMissing(ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN);
    }

    private void createIfMissing(String email, String password, Role role) {
        if (!userRepository.existsByEmail(email)) {
            userRepository.save(new User(email, passwordEncoder.encode(password), role));
        }
    }
}
