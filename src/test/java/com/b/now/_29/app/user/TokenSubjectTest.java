package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 토큰 주체는 새 회원의 주체를 만들고, 토큰에 담긴 버전이 회원의 지금 버전인지 판단한다. */
class TokenSubjectTest {

    private static final long USER_ID = 7L;

    @Test
    @DisplayName("initial은 그 회원 ID와 새 회원의 토큰 버전으로 주체를 만든다")
    void initialUsesInitialTokenVersion() {
        assertThat(TokenSubject.initial(USER_ID)).isEqualTo(new TokenSubject(USER_ID, User.INITIAL_TOKEN_VERSION));
    }

    @Test
    @DisplayName("토큰 버전이 지금 버전과 같으면 지금 유효한 주체다")
    void sameVersionIsCurrent() {
        assertThat(new TokenSubject(USER_ID, 2).isCurrentFor(2)).isTrue();
    }

    @Test
    @DisplayName("지금 버전이 토큰보다 높으면 (로그아웃한 뒤) 유효하지 않다")
    void olderVersionIsNotCurrent() {
        assertThat(new TokenSubject(USER_ID, 2).isCurrentFor(3)).isFalse();
    }

    @Test
    @DisplayName("토큰 버전이 지금 버전보다 높아도 같지 않으므로 유효하지 않다")
    void newerVersionIsNotCurrent() {
        assertThat(new TokenSubject(USER_ID, 1).isCurrentFor(0)).isFalse();
    }
}
