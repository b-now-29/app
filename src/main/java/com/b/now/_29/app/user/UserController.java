package com.b.now._29.app.user;

import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;
    private final AuthCookies authCookies;

    public UserController(UserService userService, TokenService tokenService, AuthCookies authCookies) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.authCookies = authCookies;
    }

    @PostMapping
    public ResponseEntity<UserResponse> signUp(
            @Valid @RequestBody SignUpRequest request, HttpServletRequest httpRequest) {
        UserResponse response = userService.signUp(
                request.email(), request.password(), request.nickname(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookies.issue(tokenService.issue(response.id())).toString())
                .body(response);
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
}
