package com.b.now._29.app.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번호를 정해 회원을 저장하는 "한 번 시도" (D12). 메서드마다 자기 트랜잭션으로 돌고, 제약 위반은 그대로 던진다. 재시도는 트랜잭션 밖에서 {@link
 * UserService}가 한다.
 */
@Service
public class NicknameTagAssigner {

    private final UserRepository userRepository;

    public NicknameTagAssigner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(String normalizedEmail, String passwordHash, Role role, Nickname nickname) {
        User user = new User(normalizedEmail, passwordHash, role, nickname, nextTag(nickname));
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    /** 엔티티 메서드로 바꾸고 flush한다. merge하지 않으므로 {@code @DynamicUpdate}가 바뀐 컬럼만 UPDATE한다. */
    @Transactional
    public UserResponse changeNickname(long userId, Nickname nickname) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!user.hasNickname(nickname)) {
            user.changeNickname(nickname, nextTag(nickname));
            userRepository.flush();
        }
        return UserResponse.from(user);
    }

    private int nextTag(Nickname nickname) {
        return userRepository
                .findMaxTagByNickname(nickname.value())
                .map(NicknameTag::nextTag)
                .orElse(NicknameTag.FIRST_TAG);
    }
}
