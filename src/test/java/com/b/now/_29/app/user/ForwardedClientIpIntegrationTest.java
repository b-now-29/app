package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.ClientIps;
import com.b.now._29.app.support.TestJwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 Tomcat에서 리버스 프록시 (Caddy)가 보낸 {@code X-Forwarded-For}로 클라이언트 IP를 구분하는지 확인한다
 * (docs/features/login-hardening.md R11, D12: {@code server.forward-headers-strategy: native}).
 *
 * <p>MockMvc는 Tomcat의 {@code RemoteIpValve}를 거치지 않으므로 {@code RANDOM_PORT}로 띄운 서버에 HTTP로 요청한다. 테스트
 * 클라이언트는 루프백 (127.0.0.1)에서 접속하므로 Tomcat이 기본으로 믿는 프록시다.
 */
@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=" + TestJwts.SECRET)
@Import(TestcontainersConfiguration.class)
class ForwardedClientIpIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final int LOGIN_PAIR_MAX = 5;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("X-Forwarded-For가 다른 두 요청은 다른 IP로 센다: 한 IP의 쌍이 막혀도 다른 IP는 로그인된다 (R11, D12)")
    void forwardedForSeparatesClients() throws Exception {
        String email = "forwarded-" + UUID.randomUUID() + "@example.com";
        String attacker = ClientIps.unique();
        String owner = ClientIps.unique();
        assertThat(post("/api/v1/users", signUpBody(email), ClientIps.unique()).statusCode())
                .isEqualTo(201);

        for (int i = 0; i < LOGIN_PAIR_MAX; i++) {
            assertThat(post("/api/v1/auth/login", credentials(email, "wrong-password"), attacker)
                            .statusCode())
                    .isEqualTo(401);
        }

        assertThat(post("/api/v1/auth/login", credentials(email, PASSWORD), attacker)
                        .statusCode())
                .isEqualTo(401);
        assertThat(post("/api/v1/auth/login", credentials(email, PASSWORD), owner)
                        .statusCode())
                .isEqualTo(200);
    }

    private HttpResponse<String> post(String path, String body, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private static String signUpBody(String email) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"프록시\"}".formatted(email, PASSWORD);
    }
}
