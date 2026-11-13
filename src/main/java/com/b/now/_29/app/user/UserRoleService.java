package com.b.now._29.app.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 요청마다 DB에서 role을 확인한다. JWT에는 role을 넣지 않으므로 role 변경이 바로 반영된다 (D3). */
@Service
@Transactional(readOnly = true)
public class UserRoleService {

    private final UserRepository userRepository;

    public UserRoleService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isAdmin(long userId) {
        return userRepository.findById(userId).map(User::isAdmin).orElse(false);
    }
}
