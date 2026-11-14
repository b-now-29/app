package com.b.now._29.app.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(UserController.BASE_PATH)
public class UserController {

    static final String BASE_PATH = "/api/v1/users";

    private final UserService userService;
    private final AuthCookies authCookies;

    public UserController(UserService userService, AuthCookies authCookies) {
        this.userService = userService;
        this.authCookies = authCookies;
    }

    @PostMapping
    public ResponseEntity<UserResponse> signUp(
            @Valid @RequestBody SignUpRequest request, HttpServletRequest httpRequest) {
        SignInResult result = userService.signUp(
                request.email(), request.password(), request.nickname(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, authCookies.issueFor(result.tokenSubject()))
                .body(result.user());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getUser(userId);
    }

    @PutMapping("/me/nickname")
    public UserResponse changeNickname(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody NicknameChangeRequest request) {
        return userService.changeNickname(userId, request.nickname());
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletResponse response) {
        TokenSubject subject = userService.changePassword(userId, request.currentPassword(), request.newPassword());
        // 연장 필터가 먼저 붙인 옛 버전 쿠키를 새 버전 쿠키 하나로 덮어쓴다 (D8)
        authCookies.overwriteFor(response, subject);
        return ResponseEntity.noContent().build();
    }
}
