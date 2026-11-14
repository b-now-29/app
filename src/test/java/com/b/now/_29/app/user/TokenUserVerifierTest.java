package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 토큰의 회원이 있고 토큰 버전이 그 회원의 지금 버전과 같을 때만 유효하다. */
@ExtendWith(MockitoExtension.class)
class TokenUserVerifierTest {

    private static final long USER_ID = 1L;

    @Mock
    UserRepository userRepository;

    TokenUserVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new TokenUserVerifier(userRepository);
    }

    @Test
    @DisplayName("회원이 있고 버전이 같으면 유효하다")
    void sameVersionIsCurrent() {
        given(userRepository.findTokenVersionById(USER_ID)).willReturn(Optional.of(2));

        assertThat(verifier.isCurrent(new TokenSubject(USER_ID, 2))).isTrue();
    }

    @Test
    @DisplayName("회원의 버전이 토큰보다 높으면 (로그아웃한 뒤) 유효하지 않다")
    void olderTokenVersionIsNotCurrent() {
        given(userRepository.findTokenVersionById(USER_ID)).willReturn(Optional.of(3));

        assertThat(verifier.isCurrent(new TokenSubject(USER_ID, 2))).isFalse();
    }

    @Test
    @DisplayName("토큰 버전이 회원의 버전과 다르면 높아도 유효하지 않다")
    void newerTokenVersionIsNotCurrent() {
        given(userRepository.findTokenVersionById(USER_ID)).willReturn(Optional.of(0));

        assertThat(verifier.isCurrent(new TokenSubject(USER_ID, 1))).isFalse();
    }

    @Test
    @DisplayName("회원이 없으면 유효하지 않다")
    void unknownUserIsNotCurrent() {
        given(userRepository.findTokenVersionById(USER_ID)).willReturn(Optional.empty());

        assertThat(verifier.isCurrent(new TokenSubject(USER_ID, 0))).isFalse();
    }
}
