package com.b.now._29.app.config;

import com.b.now._29.app.user.AuthCookies;
import com.b.now._29.app.user.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 쿠키의 토큰이 유효하면 인증한다. 잘못된 토큰은 거부하지 않고 비회원으로 둔다. */
class AccessTokenFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AuthCookies authCookies;

    AccessTokenFilter(TokenService tokenService, AuthCookies authCookies) {
        this.tokenService = tokenService;
        this.authCookies = authCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        authCookies.tokenFrom(request).flatMap(tokenService::parseUserId).ifPresent(userId -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
            SecurityContextHolder.setContext(context);
        });
        filterChain.doFilter(request, response);
    }
}
