package com.b.now._29.app.user;

import jakarta.persistence.Embeddable;

/**
 * 닉네임과 번호를 묶은 값 객체. 회원은 이 조합으로 유일하다 (R2). 번호 규칙은 max + 1이다 (R3, D3).
 */
@Embeddable
public record NicknameTag(String nickname, int tag) {

    /** 그 닉네임을 쓰는 회원이 없을 때의 번호. */
    public static final int FIRST_TAG = 1;

    public NicknameTag {
        if (tag < 1) {
            throw new IllegalArgumentException("번호는 1 이상이어야 합니다.");
        }
    }

    public NicknameTag(Nickname nickname, int tag) {
        this(nickname.value(), tag);
    }

    /** 그 닉네임의 가장 큰 번호를 받아 다음 번호를 돌려준다. */
    public static int nextTag(int currentMaxTag) {
        return currentMaxTag + 1;
    }
}
