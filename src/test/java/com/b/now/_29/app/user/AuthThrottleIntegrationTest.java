package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.ClientIps;
import com.b.now._29.app.support.TestJwts;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 로그인·가입 시도 제한을 실제 보안 필터, Service, DB와 운영 수치 (로그인 쌍 5회, IP 50회, 가입 IP 10회)로 확인한다
 * (docs/features/login-hardening.md R1~R4, R8, R9, D10).
 *
 * <p>횟수는 앱 메모리에 남고 컨텍스트를 공유하는 다른 테스트와 섞일 수 있으므로, 테스트마다 {@link ClientIps#unique()}로 새 IP를 쓴다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthThrottleIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final int LOGIN_PAIR_MAX = 5;
    private static final int LOGIN_IP_MAX = 50;
    private static final int SIGN_UP_IP_MAX = 10;
    private static final String TOO_MANY_SIGN_UPS = "가입 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.";

    // matches가 불릴 때의 트랜잭션 상태를 기록하려고 운영 인코더를 감싼다 (R8)
    @MockitoSpyBean
    PasswordEncoder passwordEncoder;

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("같은 이메일·IP로 5번 틀리면 맞는 비밀번호도 일반 실패와 같은 401이고, 다른 IP에서는 로그인된다 (R1, R3, D1, D3)")
    void pairIsBlockedAfterFiveFailures() throws Exception {
        String email = signUp();
        String ip = ClientIps.unique();
        // 4번까지는 막히지 않는다. 성공하면 쌍 횟수가 지워진다 (R4)
        failLogin(email, ip, LOGIN_PAIR_MAX - 1);
        login(email, PASSWORD, ip).andExpect(status().isOk());

        failLogin(email, ip, LOGIN_PAIR_MAX - 1);
        MvcResult lastFailure = login(email, WRONG_PASSWORD, ip)
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult blocked = login(email, PASSWORD, ip)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(body(blocked)).isEqualTo(body(lastFailure));

        login(email, PASSWORD, ClientIps.unique()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 IP에서 여러 이메일로 50번 틀리면 그 IP의 모든 로그인이 401이고, 성공해도 IP 횟수는 지워지지 않는다 (R2, R4)")
    void ipIsBlockedAfterFiftyFailures() throws Exception {
        String email = signUp();
        String ip = ClientIps.unique();
        for (int i = 0; i < LOGIN_IP_MAX - 1; i++) {
            login(uniqueEmail(), WRONG_PASSWORD, ip).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, ip).andExpect(status().isOk());

        login(uniqueEmail(), WRONG_PASSWORD, ip).andExpect(status().isUnauthorized());

        login(email, PASSWORD, ip).andExpect(status().isUnauthorized());
        login(email, PASSWORD, ClientIps.unique()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 IP의 11번째 가입 시도는 성공·409와 상관없이 429 ProblemDetail이고 회원이 생기지 않는다 (R9, D4, D5)")
    void eleventhSignUpIsTooManyRequests() throws Exception {
        String ip = ClientIps.unique();
        String duplicate = uniqueEmail();
        signUp(duplicate, ip).andExpect(status().isCreated());
        signUp(duplicate, ip).andExpect(status().isConflict());
        for (int i = 2; i < SIGN_UP_IP_MAX; i++) {
            signUp(uniqueEmail(), ip).andExpect(status().isCreated());
        }
        String late = uniqueEmail();

        signUp(late, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value(TOO_MANY_SIGN_UPS))
                .andExpect(jsonPath("$.instance").value("/api/v1/users"));

        assertThat(userRepository.existsByEmail(late)).isFalse();
        signUp(late, ClientIps.unique()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("로그인의 BCrypt 비교는 성공·틀린 비밀번호·없는 이메일 모두 DB 트랜잭션 밖에서 한다 (R7, R8, D10)")
    void passwordMatchingRunsOutsideTransaction() throws Exception {
        String email = signUp();
        List<Boolean> transactionActive = new CopyOnWriteArrayList<>();
        willAnswer(invocation -> {
                    transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .given(passwordEncoder)
                .matches(any(), any());

        login(email, PASSWORD, ClientIps.unique()).andExpect(status().isOk());
        login(email, WRONG_PASSWORD, ClientIps.unique()).andExpect(status().isUnauthorized());
        login(uniqueEmail(), WRONG_PASSWORD, ClientIps.unique()).andExpect(status().isUnauthorized());

        // 없는 이메일도 더미 해시와 비교하므로 (R7) 세 번 모두 기록된다
        assertThat(transactionActive).hasSize(3).containsOnly(false);
    }

    private void failLogin(String email, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(email, WRONG_PASSWORD, ip).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(ClientIps.from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    /** 다른 테스트와 겹치지 않는 IP에서 새 회원을 가입시키고 이메일을 돌려준다. */
    private String signUp() throws Exception {
        String email = uniqueEmail();
        signUp(email, ClientIps.unique()).andExpect(status().isCreated());
        return email;
    }

    private ResultActions signUp(String email, String ip) throws Exception {
        return mvc.perform(post("/api/v1/users")
                .with(ClientIps.from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"제한테스트\"}"
                        .formatted(email, PASSWORD)));
    }

    private static String uniqueEmail() {
        return "throttle-" + UUID.randomUUID() + "@example.com";
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
