package com.b.now._29.app.user.admin;

import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import com.b.now._29.app.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<AdminUserResponse> getUsers() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Transactional
    public AdminUserResponse changeRole(long id, Role role, long actorId) {
        User user = userRepository.findById(id).orElseThrow(() -> new AdminUserNotFoundException(id));
        user.changeRole(role, actorId);
        return AdminUserResponse.from(user);
    }
}
