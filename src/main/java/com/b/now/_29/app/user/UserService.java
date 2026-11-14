package com.b.now._29.app.user;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
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

    /**
     * 없는 이메일 로그인에서 비교할 강도 10의 BCrypt 해시 (D9). 널리 알려진 예제 해시라 맞는 비밀번호가 있을 수 있다. 없는 이메일이 로그인되지 않는 것은
     * {@code found.isEmpty()} 검사 덕분이지 이 해시가 안 맞아서가 아니다.
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NicknameTagAssigner assigner;

    private final AuthLimiters limiters;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NicknameTagAssigner assigner,
            AuthLimiters limiters) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.assigner = assigner;
        this.limiters = limiters;
    }

    /** 성공·실패와 상관없이 시도로 센다 (R9). 막혔으면 아무것도 하지 않는다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse signUp(String email, String password, String nickname, String clientIp) {
        if (!limiters.signUpIp().tryAcquire(clientIp)) {
            throw new TooManySignUpAttemptsException(limiters.signUpIp().retryAfter(clientIp));
        }
        return register(email, password, nickname, Role.USER);
    }

    /** 관리자·테스트 계정용이라 가입 제한을 받지 않는다 (R10, R17). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse createAccount(String email, String password, String nickname, Role role) {
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

    /**
     * BCrypt 비교가 DB 커넥션을 잡지 않도록 트랜잭션 밖에서 실행한다 (R8). 조회는 Repository의 자체 트랜잭션에서 끝나고, User에 LAZY
     * 연관이 없어 응답 변환도 안전하다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse login(String email, String password, String clientIp) {
        // 확인과 증가를 한 번에 해서 동시 요청도 한도를 넘지 못한다 (R12, D14)
        if (!limiters.loginIp().tryAcquire(clientIp)) {
            throw new InvalidCredentialsException();
        }
        String pairKey = User.normalizeEmail(email) + "|" + clientIp;
        if (!limiters.loginPair().tryAcquire(pairKey)) {
            limiters.loginIp().release(clientIp);
            throw new InvalidCredentialsException();
        }
        // BCrypt 입력 한도를 넘는 비밀번호는 가입할 수 없으므로 맞을 수 없다 (D13).
        // PostgreSQL은 NUL 문자를 거부해 500이 되므로 조회 전에 거른다 (D21)
        if (password.getBytes(StandardCharsets.UTF_8).length > SignUpRequest.MAX_PASSWORD_BYTES
                || email.indexOf('\0') >= 0) {
            throw new InvalidCredentialsException();
        }
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email));
        // 없는 이메일도 같은 강도의 비교를 해서 응답 시간으로 존재 여부를 알 수 없게 한다 (R7)
        String hash = found.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean matched = passwordEncoder.matches(password, hash);
        if (found.isEmpty() || !matched) {
            throw new InvalidCredentialsException();
        }
        // 성공하면 쌍 횟수를 지우고 IP에서는 예약한 1회를 돌려준다 (R13)
        limiters.loginPair().reset(pairKey);
        limiters.loginIp().release(clientIp);
        return UserResponse.from(found.get());
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
