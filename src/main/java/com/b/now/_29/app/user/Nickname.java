package com.b.now._29.app.user;

/**
 * 닉네임. 앞뒤 공백을 지운 뒤 2~20 code point. 글자·숫자·공백 (U+0020)·{@code _ - .}만 쓸 수 있다 (D10). 대소문자는 그대로 둔다.
 */
public record Nickname(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 20;

    /** 규칙 위반 응답 문구. 가입 검증 실패 응답도 이 문구를 쓴다. */
    public static final String RULE_MESSAGE = "닉네임은 앞뒤 공백을 지운 뒤 2~20자여야 하고, 글자·숫자·공백·'_'·'-'·'.'만 쓸 수 있습니다.";

    public Nickname {
        if (!isValid(value)) {
            throw new InvalidNicknameException(RULE_MESSAGE);
        }
        value = value.strip();
    }

    /** 앞뒤 공백을 지우기 전의 값을 받아 규칙에 맞는지 본다. null은 규칙 위반이다. */
    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String stripped = raw.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        return stripped.codePoints().allMatch(Nickname::isAllowed);
    }

    private static boolean isAllowed(int cp) {
        return switch (cp) {
            // 글자로 분류되지만 보이지 않는 한글 채움 문자
            case 0x3164, 0x115F, 0x1160, 0xFFA0 -> false;
            case ' ', '_', '-', '.' -> true;
            default -> Character.isLetter(cp) || Character.isDigit(cp);
        };
    }
}
