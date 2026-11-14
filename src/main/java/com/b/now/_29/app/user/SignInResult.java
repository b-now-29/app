package com.b.now._29.app.user;

/** 가입·로그인 결과. 응답 본문에 쓸 회원 정보와, 쿠키 토큰에 담을 값. */
public record SignInResult(UserResponse user, TokenSubject tokenSubject) {}
