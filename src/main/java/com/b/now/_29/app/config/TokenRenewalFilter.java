package com.b.now._29.app.config;

import com.b.now._29.app.user.AuthController;
import com.b.now._29.app.user.AuthCookies;
import com.b.now._29.app.user.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/** 연장이 필요한 토큰이면 새 토큰 쿠키를 응답에 붙인다. */
class TokenRenewalFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AuthCookies authCookies;

    TokenRenewalFilter(TokenService tokenService, AuthCookies authCookies) {
        this.tokenService = tokenService;
        this.authCookies = authCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 응답이 커밋된 뒤에는 헤더를 붙일 수 없으므로 doFilter 전에 붙인다.
        // 로그아웃 응답에는 만료 쿠키만 있어야 하므로 로그아웃 경로는 연장하지 않는다.
        if (!AuthController.LOGOUT_PATH.equals(request.getRequestURI())) {
            authCookies
                    .tokenFrom(request)
                    .flatMap(tokenService::renew)
                    .ifPresent(renewed -> response.addHeader(HttpHeaders.SET_COOKIE, authCookies.issue(renewed)));
        }
        filterChain.doFilter(request, response);
    }
}
