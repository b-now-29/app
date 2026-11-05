package com.b.now._29.app.config;

import com.b.now._29.app.user.AuthCookies;
import com.b.now._29.app.user.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /api/v1/users/me}만 인증을 요구하고 나머지는 모두 허용한다 (D4).
 *
 * <p>토큰 검증은 직접 만든 필터로 한다. 잘못된 토큰은 거부하지 않고 비회원으로 처리한다 (D3).
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService, JsonMapper jsonMapper) {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/users/me")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorizedEntryPoint(jsonMapper)))
                .addFilterBefore(new AccessTokenFilter(tokenService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** ProblemDetail을 앱의 JSON 매퍼로 쓴다 (D22). Content-Type은 charset 없이 둔다. */
    private static AuthenticationEntryPoint unauthorizedEntryPoint(JsonMapper jsonMapper) {
        JsonMapper mapper = jsonMapper
                .rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
        return (request, response, exception) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
            problem.setInstance(URI.create(request.getRequestURI()));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getOutputStream().write(mapper.writeValueAsBytes(problem));
        };
    }

    private static final class AccessTokenFilter extends OncePerRequestFilter {

        private final TokenService tokenService;

        AccessTokenFilter(TokenService tokenService) {
            this.tokenService = tokenService;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            tokenFrom(request).flatMap(tokenService::parseUserId).ifPresent(userId -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
                SecurityContextHolder.setContext(context);
            });
            filterChain.doFilter(request, response);
        }

        private static java.util.Optional<String> tokenFrom(HttpServletRequest request) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return java.util.Optional.empty();
            }
            for (Cookie cookie : cookies) {
                if (AuthCookies.NAME.equals(cookie.getName())) {
                    return java.util.Optional.ofNullable(cookie.getValue());
                }
            }
            return java.util.Optional.empty();
        }
    }
}
