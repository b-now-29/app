package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.TestJwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TestAccountDefaultProfileIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    UserRepository userRepository;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("기본 프로필로 시작하면 테스트 계정이 생기지 않고 그 계정으로 로그인할 수 없다 (R11)")
    void testAccountDoesNotExist() throws Exception {
        assertThat(context.getBeansOfType(TestAccountInitializer.class)).isEmpty();
        assertThat(userRepository.existsByEmail("test@example.com")).isFalse();
        assertThat(userRepository.existsByEmail("admin@example.com")).isFalse();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"test@example.com\", \"password\": \"test1234!\"}"))
                .andExpect(status().isUnauthorized());
    }
}
