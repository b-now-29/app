package com.b.now._29.app.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 비교가 끝난 뒤 저장만 하는 짧은 쓰기 트랜잭션 (D3). BCrypt 비교·해시는 트랜잭션 밖의 {@link UserService}가 하고, 같은 클래스 자기 호출은
 * 트랜잭션이 적용되지 않으므로 {@link NicknameTagAssigner}처럼 별도 빈으로 둔다.
 */
@Service
public class PasswordWriter {

    private final UserRepository userRepository;

    public PasswordWriter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 조회 후 엔티티 메서드로 바꾼다. 비교 뒤 다른 요청이 바꿨는지 확인(D5)도 엔티티가 한다. merge하지 않아 {@code @DynamicUpdate}가 바뀐 컬럼만
     * UPDATE한다.
     */
    @Transactional
    public TokenSubject changePassword(long userId, String expectedHash, String newHash) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.changePassword(expectedHash, newHash);
        return user.tokenSubject();
    }
}
