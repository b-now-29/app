package com.b.now._29.app.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토큰의 회원이 지금 유효한지 확인한다. 서명과 기한이 맞는 토큰이라도 회원이 없으면 비회원으로 다루기 위해 보안 필터가 요청마다 쓴다. */
@Service
@Transactional(readOnly = true)
public class TokenUserVerifier {

    private final UserRepository userRepository;

    public TokenUserVerifier(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean exists(long userId) {
        return userRepository.existsById(userId);
    }
}
