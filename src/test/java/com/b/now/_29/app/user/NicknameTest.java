package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 닉네임 규칙 (docs/features/nickname.md R1, R7, D7, D10).
 *
 * <p>구현할 것:
 *
 * <ul>
 *   <li>{@code record Nickname(String value)} — compact constructor에서 앞뒤 공백을 {@code strip()}으로 지운 뒤 검증한다. 지운 뒤
 *       2~20 code point. 허용 목록 (D10): {@code Character.isLetter}, {@code Character.isDigit}, 공백 U+0020, {@code _}
 *       {@code -} {@code .}만. 단 한글 채움 문자 (U+3164, U+115F, U+1160, U+FFA0)는 글자지만 거부한다. null도 거부한다
 *   <li>{@code InvalidNicknameException extends RuntimeException} — 규칙 위반. ApiExceptionHandler에서 400
 * </ul>
 *
 * <p>테스트 문자열은 보이지 않는 문자가 섞여도 읽을 수 있도록 {@code \\u} 이스케이프로 쓴다.
 */
class NicknameTest {

    private static final String MATH_A = "𝒜"; // U+1D49C 𝒜 (글자, Lu), UTF-16 2글자지만 code point 1개

    static Stream<Arguments> validNicknames() {
        return Stream.of(
                arguments("한글 2자 (최소)", "철수", "철수"),
                arguments("영문 20자 (최대)", "a".repeat(20), "a".repeat(20)),
                arguments("가운데 공백은 그대로", "철 수", "철 수"),
                arguments("앞뒤 공백은 지운다", "  철수  ", "철수"),
                arguments("지운 뒤 2자면 된다", " ab ", "ab"),
                arguments("대소문자를 그대로 둔다 (R7)", "Kim", "Kim"),
                arguments("숫자만", "2026", "2026"),
                arguments("한글·영문·숫자", "Kim철수99", "Kim철수99"),
                arguments("허용 기호 _ - .", "kim_99-a.b", "kim_99-a.b"),
                arguments("허용 기호만", "_-.", "_-."),
                arguments("한글 자모 (채움 문자 아님)", "ㅋㅋ", "ㅋㅋ"),
                arguments("완성형 악센트 글자 (D10)", "José", "José"),
                arguments("일본어", "こんにちは", "こんにちは"),
                arguments("보조 평면 글자 20개는 code point 20개 (D7)", MATH_A.repeat(20), MATH_A.repeat(20)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validNicknames")
    @DisplayName("허용 문자만 쓴 닉네임은 앞뒤 공백만 지우고 그대로 저장한다 (R1, R7, D7, D10)")
    void acceptsValidNickname(String description, String raw, String expected) {
        assertThat(new Nickname(raw).value()).isEqualTo(expected);
        assertThat(Nickname.isValid(raw)).isTrue();
    }

    static Stream<Arguments> invalidLengths() {
        return Stream.of(
                arguments("빈 문자열", ""),
                arguments("공백뿐", "   "),
                arguments("1자", "a"),
                arguments("지운 뒤 1자", "  가  "),
                arguments("21자", "a".repeat(21)),
                arguments("지운 뒤에도 21자", " " + "가".repeat(21) + " "),
                arguments("보조 평면 글자 21개 (UTF-16 42글자)", MATH_A.repeat(21)),
                arguments("보조 평면 글자 1개 (UTF-16 2글자지만 1자)", MATH_A));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLengths")
    @DisplayName("앞뒤 공백을 지운 뒤 2~20 code point가 아니면 InvalidNicknameException이다 (R1, D7)")
    void rejectsInvalidLength(String description, String raw) {
        assertThatThrownBy(() -> new Nickname(raw)).isInstanceOf(InvalidNicknameException.class);
        assertThat(Nickname.isValid(raw)).isFalse();
    }

    // 허용 목록 밖의 문자 (R1, D10, 제약 사항 코드 리뷰). 길이는 모두 2~20자라 문자 규칙으로만 거부된다
    static Stream<Arguments> disallowedCharacters() {
        return Stream.of(
                // # 과 다른 기호
                arguments("# 포함", "철수#1"),
                arguments("# 만", "##"),
                arguments("느낌표", "kim_99!"),
                arguments("@", "kim@home"),
                arguments("괄호", "(철수)"),
                arguments("한글 중점", "철·수"),
                // U+0020이 아닌 공백
                arguments("전각 공백 U+3000", "철\u3000수"),
                arguments("줄바꿈 없는 공백 U+00A0", "철\u00A0수"),
                // 제어 문자
                arguments("NUL 가운데", "철\0수"),
                arguments("NUL 끝 (strip이 지우지 않는다)", "철수\0"),
                arguments("NUL 앞", "\0철수"),
                arguments("가운데 탭", "철\t수"),
                arguments("가운데 줄바꿈", "철\n수"),
                arguments("ESC", "철\u001B수"),
                arguments("DEL", "철\u007F수"),
                arguments("C1 제어 문자", "철\u0085수"),
                // 형식 문자
                arguments("U+200B 폭 없는 공백 가운데", "철\u200B수"),
                arguments("U+200B 끝", "철수\u200B"),
                arguments("U+200B만 2개", "\u200B\u200B"),
                arguments("U+FEFF BOM 앞", "\uFEFF철수"),
                arguments("U+200D 폭 없는 결합자", "철\u200D수"),
                arguments("U+202E 오른쪽에서 왼쪽 덮어쓰기", "철\u202E수"),
                arguments("U+00AD 소프트 하이픈", "철\u00AD수"),
                // 짝 안 맞는 surrogate
                arguments("짝 없는 high surrogate 2개", "\uD800\uD800"),
                arguments("짝 없는 high surrogate 가운데", "철\uD800수"),
                arguments("짝 없는 low surrogate 가운데", "철\uDC00수"),
                arguments("순서가 뒤집힌 surrogate", "철\uDC00\uD800수"),
                // 한글 채움 문자 (글자로 분류되지만 보이지 않는다)
                arguments("한글 채움 문자 U+3164 가운데", "철\u3164수"),
                arguments("한글 채움 문자 U+3164만", "\u3164\u3164"),
                arguments("한글 초성 채움 U+115F", "철\u115F수"),
                arguments("한글 중성 채움 U+1160", "철\u1160수"),
                arguments("반각 한글 채움 U+FFA0", "철\uFFA0수"),
                // 이모지 (D10: 지금은 모두 거부)
                arguments("이모지 2개", "😀😀"),
                arguments("한글과 이모지", "철수😀"),
                arguments("ZWJ 조합 이모지 (가족)", "철수👨\u200D👩\u200D👧"),
                arguments("피부색 이모지", "철수👍🏽"),
                arguments("국기 이모지", "철수🇰🇷"),
                arguments("이모지 선택자 U+FE0F", "철수❤\uFE0F"),
                arguments("선택자만 붙은 글자", "철수\uFE0F"),
                // 결합 문자 (D10 알려진 한계)
                arguments("결합 악센트 U+0301", "Jose\u0301"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("disallowedCharacters")
    @DisplayName("글자·숫자·공백·_ - . 밖의 문자와 한글 채움 문자는 InvalidNicknameException이다 (R1, D10)")
    void rejectsDisallowedCharacters(String description, String raw) {
        assertThatThrownBy(() -> new Nickname(raw)).isInstanceOf(InvalidNicknameException.class);
        assertThat(Nickname.isValid(raw)).isFalse();
    }

    @Test
    @DisplayName("null은 InvalidNicknameException이다 (R1)")
    void rejectsNull() {
        assertThatThrownBy(() -> new Nickname(null)).isInstanceOf(InvalidNicknameException.class);
        assertThat(Nickname.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 닉네임은 같은 닉네임이고, 대소문자가 다르면 다른 닉네임이다 (R1, R7)")
    void equality() {
        assertThat(new Nickname(" 철수 ")).isEqualTo(new Nickname("철수"));
        assertThat(new Nickname("Kim")).isNotEqualTo(new Nickname("kim"));
    }
}
