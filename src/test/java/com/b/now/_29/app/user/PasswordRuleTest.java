package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 로그인·비밀번호 변경·검증 어노테이션이 함께 쓰는 비밀번호 바이트 규칙 (docs/features/password-change.md R3, D6, D11, D13).
 */
class PasswordRuleTest {

    private static final String HANGUL_SYLLABLE = "가"; // UTF-8 3바이트
    private static final String MATH_A = "𝒜"; // UTF-8 4바이트, UTF-16 2글자

    @Test
    @DisplayName("BCrypt 입력 한도라 최대 바이트는 72이고, 가입 검증의 한도도 같은 값이다 (D13)")
    void maxBytesIsBcryptLimit() {
        assertThat(PasswordRule.MAX_BYTES).isEqualTo(72);
        assertThat(SignUpRequest.MAX_PASSWORD_BYTES).isEqualTo(PasswordRule.MAX_BYTES);
    }

    @Test
    @DisplayName("UTF-8로 정확히 72바이트면 한도를 넘지 않는다")
    void atLimitDoesNotExceed() {
        assertThat(PasswordRule.exceedsMaxBytes("a".repeat(72))).isFalse();
        assertThat(PasswordRule.exceedsMaxBytes(HANGUL_SYLLABLE.repeat(24))).isFalse();
        assertThat(PasswordRule.exceedsMaxBytes(MATH_A.repeat(18))).isFalse();
    }

    @Test
    @DisplayName("글자 수가 아니라 UTF-8 바이트로 세어, 72바이트를 넘으면 한도를 넘는다")
    void overLimitExceeds() {
        assertThat(PasswordRule.exceedsMaxBytes("a".repeat(73))).isTrue();
        // 25자지만 75바이트
        assertThat(PasswordRule.exceedsMaxBytes(HANGUL_SYLLABLE.repeat(25))).isTrue();
        // 71바이트 + 3바이트
        assertThat(PasswordRule.exceedsMaxBytes("a".repeat(71) + HANGUL_SYLLABLE))
                .isTrue();
        // UTF-16 38글자지만 76바이트
        assertThat(PasswordRule.exceedsMaxBytes(MATH_A.repeat(19))).isTrue();
    }

    @Test
    @DisplayName("짧은 비밀번호와 빈 문자열은 한도를 넘지 않는다")
    void shortDoesNotExceed() {
        assertThat(PasswordRule.exceedsMaxBytes("")).isFalse();
        assertThat(PasswordRule.exceedsMaxBytes("short")).isFalse();
    }

    // 표시 이름에 NUL 문자가 들어가지 않도록 name을 번호로 둔다
    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"\0password1234", "pass\0word1234", "password1234\0", "\0"})
    @DisplayName("어느 자리든 NUL 문자가 있으면 containsNul은 참이다 (D11)")
    void detectsNul(String password) {
        assertThat(PasswordRule.containsNul(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1234", "", "        ", "비밀번호1234", "pass\tword\n1234", "\\u0000"})
    @DisplayName("NUL이 아닌 제어 문자, 공백, 한글, 글자 그대로의 \\u0000 문자열은 NUL 문자가 아니다 (D11)")
    void otherCharactersAreNotNul(String password) {
        assertThat(PasswordRule.containsNul(password)).isFalse();
    }
}
