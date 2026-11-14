package com.b.now._29.app.user;

/** 토큰이 가리키는 회원과, 토큰을 발급할 때 그 회원의 토큰 버전. */
public record TokenSubject(long userId, int tokenVersion) {

    /** 방금 가입한 회원의 주체. 아직 로그아웃한 적이 없으므로 처음 버전이다. */
    public static TokenSubject initial(long userId) {
        return new TokenSubject(userId, User.INITIAL_TOKEN_VERSION);
    }

    /** 토큰의 버전이 회원의 지금 버전과 같을 때만 참이다. */
    public boolean isCurrentFor(int currentVersion) {
        return tokenVersion == currentVersion;
    }
}
