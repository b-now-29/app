package com.b.now._29.app.user;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보안 계층과 랭킹이 요청마다 쓰는 회원 role 조회. JWT에는 role을 넣지 않으므로 role 변경이 바로 반영된다. */
@Service
@Transactional(readOnly = true)
public class UserRoleService {

    private final UserRepository userRepository;

    public UserRoleService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 없는 회원은 empty. */
    public Optional<Role> findRole(long userId) {
        return userRepository.findById(userId).map(User::getRole);
    }
}
