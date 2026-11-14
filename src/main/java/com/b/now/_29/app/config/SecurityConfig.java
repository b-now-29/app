package com.b.now._29.app.config;

import com.b.now._29.app.user.AuthCookies;
import com.b.now._29.app.user.TokenService;
import com.b.now._29.app.user.TokenUserVerifier;
import com.b.now._29.app.user.UserRoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /api/v1/users/me}와 그 하위 경로, 자기 채점 저장만 인증을 요구하고 {@code /api/v1/admin/**}은 관리자만 허용한다. 나머지는 모두 허용한다.
 *
 * <p>토큰 검증과 연장은 직접 만든 필터로 한다. 잘못된 토큰은 거부하지 않고 비회원으로 처리한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TokenService tokenService,
            JsonMapper jsonMapper,
            UserRoleService userRoleService,
            TokenUserVerifier tokenUserVerifier,
            AuthCookies authCookies) {
        JsonMapper problemMapper = jsonMapper
                .rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/admin/**")
                        .access(adminOnly(userRoleService))
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/questions/*/self-grades")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, exception) ->
                                writeProblem(problemMapper, request, response, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(problemMapper, request, response, HttpStatus.FORBIDDEN, "접근 권한이 없습니다.")))
                .addFilterBefore(
                        new AccessTokenFilter(tokenService, authCookies, tokenUserVerifier),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new TokenRenewalFilter(tokenService, authCookies), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 관리자 경로는 요청마다 DB에서 role을 확인한다. 비회원은 거부해 401 entry point로, 회원인데 관리자가 아니면 거부해 403 handler로 간다.
     */
    private static AuthorizationManager<RequestAuthorizationContext> adminOnly(UserRoleService userRoleService) {
        return (authentication, context) -> {
            boolean admin = AuthenticatedUser.of(authentication.get())
                    .map(userRoleService::isAdmin)
                    .orElse(false);
            return new AuthorizationDecision(admin);
        };
    }

    /** ProblemDetail을 앱의 JSON 매퍼로 쓴다. Content-Type은 charset 없이 둔다. */
    private static void writeProblem(
            JsonMapper mapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(mapper.writeValueAsBytes(problem));
    }
}
