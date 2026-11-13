package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 닉네임과 번호를 묶은 값 객체와 번호 규칙 (docs/features/nickname.md R3, D3, 제약 사항 Standards 리뷰)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code @Embeddable public record NicknameTag(String nickname, int tag)} — {@code User}의 {@code nickname}, {@code tag}
 *       컬럼을 이 값 객체 하나로 매핑한다 (컬럼 이름은 그대로). tag가 1보다 작으면 {@code IllegalArgumentException}
 *   <li>{@code NicknameTag(Nickname nickname, int tag)} — 검증된 닉네임으로 만드는 생성자 ({@code nickname.value()} 저장)
 *   <li>{@code NicknameTag.FIRST_TAG = 1} — 그 닉네임을 쓰는 회원이 없을 때의 번호
 *   <li>{@code static int NicknameTag.nextTag(int currentMaxTag)} — 그 닉네임의 가장 큰 번호를 받아 다음 번호 (+1)를 돌려주는 순수 함수.
 *       Service는 {@code findMaxTagByNickname(..).map(NicknameTag::nextTag).orElse(NicknameTag.FIRST_TAG)}로 쓴다
 * </ul>
 */
class NicknameTagTest {

    @Test
    @DisplayName("그 닉네임을 쓰는 회원이 없으면 번호는 1이다 (R3)")
    void firstTagIsOne() {
        assertThat(NicknameTag.FIRST_TAG).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 99})
    @DisplayName("가장 큰 번호가 있으면 다음 번호는 그 번호 + 1이다 (R3, D3)")
    void nextTagIsMaxPlusOne(int currentMax) {
        assertThat(NicknameTag.nextTag(currentMax)).isEqualTo(currentMax + 1);
    }

    @Test
    @DisplayName("검증된 닉네임으로 만들면 공백을 지운 닉네임과 번호를 가진다 (R1)")
    void createsFromNickname() {
        NicknameTag nicknameTag = new NicknameTag(new Nickname("  철수 "), 3);

        assertThat(nicknameTag.nickname()).isEqualTo("철수");
        assertThat(nicknameTag.tag()).isEqualTo(3);
        assertThat(nicknameTag).isEqualTo(new NicknameTag("철수", 3));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("번호가 1보다 작으면 만들 수 없다 (데이터: tag 1 이상)")
    void rejectsTagBelowOne(int tag) {
        assertThatThrownBy(() -> new NicknameTag(new Nickname("철수"), tag)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NicknameTag("철수", tag)).isInstanceOf(IllegalArgumentException.class);
    }
}
