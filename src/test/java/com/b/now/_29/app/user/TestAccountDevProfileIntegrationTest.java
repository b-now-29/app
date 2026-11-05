package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.SetCookieHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** JWT_SECRET을 주지 않아도 dev 프로필만으로 시작해야 한다 (D8). */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TestAccountDevProfileIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestAccountInitializer initializer;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("dev 프로필이면 테스트 계정으로 로그인하고, 쿠키에 Secure가 없다 (R11, D8, D9)")
    void loginWithTestAccount() throws Exception {
        MvcResult result = login().andExpect(status().isOk()).andReturn();

        assertThat(SetCookieHeader.from(result).attributes()).doesNotContain("Secure");
    }

    @Test
    @DisplayName("이미 계정이 있을 때 다시 실행해도 오류 없이 그대로다 (D9)")
    void rerunIsIdempotent() throws Exception {
        assertThatCode(() -> initializer.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        login().andExpect(status().isOk());
    }

    private ResultActions login() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"test@example.com\", \"password\": \"test1234!\"}"));
    }
}
