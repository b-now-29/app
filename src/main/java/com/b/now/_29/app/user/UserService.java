package com.b.now._29.app.user;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    /**
     * 번호 경합 시 첫 시도를 포함한 총 시도 횟수 (D4).
     */
    private static final int MAX_TAG_ATTEMPTS = 3;

    private static final String TAG_CONSTRAINT = "uk_users_nickname_tag";
    private static final String EMAIL_CONSTRAINT = "users_email_key";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NicknameTagAssigner assigner;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, NicknameTagAssigner assigner) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.assigner = assigner;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse signUp(String email, String password, String nickname) {
        return register(email, password, nickname, Role.USER);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse signUp(String email, String password, String nickname, Role role) {
        return register(email, password, nickname, role);
    }

    /**
     * 트랜잭션 설정은 public 입구에만 둔다 (같은 클래스 안의 호출은 프록시를 거치지 않는다). 번호 계산과 저장은 {@link NicknameTagAssigner}의
     * 한 번 시도가 자기 트랜잭션으로 한다.
     */
    private UserResponse register(String email, String password, String nickname, Role role) {
        Nickname validNickname = new Nickname(nickname);
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new DuplicateEmailException(normalized);
        }
        String passwordHash = passwordEncoder.encode(password);
        return retryOnTagConflict(
                () -> assigner.createUser(normalized, passwordHash, role, validNickname),
                e -> isConstraint(e, EMAIL_CONSTRAINT) ? new DuplicateEmailException(normalized, e) : e);
    }

    /** 가입과 같은 이유로 트랜잭션 밖에서 재시도한다. 엔티티를 바꾸고 flush하므로 바뀐 컬럼만 UPDATE된다 (D12). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse changeNickname(long userId, String nickname) {
        Nickname validNickname = new Nickname(nickname);
        return retryOnTagConflict(() -> assigner.changeNickname(userId, validNickname), e -> e);
    }

    /**
     * 번호 경합 ({@code uk_users_nickname_tag})이면 한 번 시도를 다시 부른다 (D4). 제약 위반이 난 트랜잭션은 rollback-only라 같은
     * 트랜잭션에서 다시 시도할 수 없으므로 시도마다 별도 트랜잭션이어야 한다. 그 밖의 제약 위반은 {@code translate}가 정한 예외로 던진다.
     */
    private <T> T retryOnTagConflict(
            Supplier<T> attempt, Function<DataIntegrityViolationException, RuntimeException> translate) {
        DataIntegrityViolationException lastConflict = null;
        for (int count = 0; count < MAX_TAG_ATTEMPTS; count++) {
            try {
                return attempt.get();
            } catch (DataIntegrityViolationException e) {
                if (!isConstraint(e, TAG_CONSTRAINT)) {
                    throw translate.apply(e);
                }
                lastConflict = e;
            }
        }
        throw lastConflict;
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
        return UserResponse.from(user);
    }

    public UserResponse getUser(long id) {
        return userRepository.findById(id).map(UserResponse::from).orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * 제약 이름으로 번호 경합과 이메일 중복을 구분한다.
     */
    private static boolean isConstraint(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equals(violation.getConstraintName())) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
