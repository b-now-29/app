package com.b.now._29.app;

import org.springframework.boot.SpringApplication;

public class TestAppApplication {

    public static void main(String[] args) {
        SpringApplication.from(AppApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("dev") // 개발용 JWT 비밀값으로 실행한다 (D8)
                .run(args);
    }
}
