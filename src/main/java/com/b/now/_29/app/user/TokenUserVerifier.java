package com.b.now._29.app.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토큰의 회원이 지금 유효한지 확인한다. 서명과 기한이 맞는 토큰이라도 회원이 없거나 로그아웃으로 무효가 된 토큰이면 비회원으로 다루기 위해 보안 필터가 요청마다 쓴다. */
@Service
@Transactional(readOnly = true)
public class TokenUserVerifier {

    private final UserRepository userRepository;

    public TokenUserVerifier(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 회원이 있고 토큰의 버전이 지금 버전과 같을 때만 참이다. */
    public boolean isCurrent(TokenSubject subject) {
        return userRepository
                .findTokenVersionById(subject.userId())
                .filter(subject::isCurrentFor)
                .isPresent();
    }
}
