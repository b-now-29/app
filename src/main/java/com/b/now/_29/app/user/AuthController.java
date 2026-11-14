package com.b.now._29.app.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

    static final String BASE_PATH = "/api/v1/auth";

    /** 로그아웃 응답에는 만료 쿠키만 두어야 하므로 연장 필터가 이 경로를 알아야 한다. */
    public static final String LOGOUT_PATH = BASE_PATH + "/logout";

    private final UserService userService;
    private final AuthCookies authCookies;

    public AuthController(UserService userService, AuthCookies authCookies) {
        this.userService = userService;
        this.authCookies = authCookies;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SignInResult result = userService.login(request.email(), request.password(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.issueFor(result.tokenSubject()))
                .body(result.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        userService.logout(userId);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.expire())
                .build();
    }
}
