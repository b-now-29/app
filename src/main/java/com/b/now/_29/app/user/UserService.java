package com.b.now._29.app.user;

import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse signUp(String email, String password) {
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new DuplicateEmailException(normalized);
        }
        try {
            User saved = userRepository.saveAndFlush(new User(normalized, passwordEncoder.encode(password)));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException(normalized, e);
        }
    }

    public UserResponse login(String email, String password) {
        // BCrypt 입력 한도를 넘는 비밀번호는 가입할 수 없으므로 맞을 수 없다 (D13)
        if (password.getBytes(StandardCharsets.UTF_8).length > SignUpRequest.MAX_PASSWORD_BYTES) {
            throw new InvalidCredentialsException();
        }
        // PostgreSQL은 NUL 문자를 거부해 500이 되므로 조회 전에 거른다 (D21)
        if (email.indexOf('\0') >= 0) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository
                .findByEmail(User.normalizeEmail(email))
                .filter(found -> passwordEncoder.matches(password, found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return toResponse(user);
    }

    public UserResponse getUser(long id) {
        return userRepository
                .findById(id)
                .map(UserService::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
