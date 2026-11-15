package com.b.now._29.app.user.admin;

import static com.b.now._29.app.support.TransactionRace.TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.TransactionRace;
import com.b.now._29.app.user.LastAdminDemotionNotAllowedException;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 두 명이 동시에 서로를 USER로 해제해도 관리자가 0명이 되지 않는지 실제 트랜잭션과 DB로 확인한다 (#36).
 *
 * <p>관리자가 A·B 둘뿐인 상태를 만든다. 같은 컨텍스트를 쓰는 다른 테스트가 남긴 ADMIN 회원은 테스트 동안 USER로 바꿔 두고 끝나면 되돌린다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@Import(TestcontainersConfiguration.class)
class LastAdminDemotionRaceIntegrationTest {

    private static final String PASSWORD = "admin1234!";

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    UserService userService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> emails = new ArrayList<>();

    private TransactionRace race;
    private List<Long> otherAdminIds = List.of();
    private long adminA;
    private long adminB;

    @BeforeEach
    void setUp() {
        race = new TransactionRace(transactionTemplate);
        otherAdminIds = jdbc.queryForList("SELECT id FROM users WHERE role = 'ADMIN'", Long.class);
        jdbc.update("UPDATE users SET role = 'USER' WHERE role = 'ADMIN'");
        adminA = newAdmin();
        adminB = newAdmin();
        assertThat(adminCount()).as("관리자가 A·B 둘뿐인 상태에서 시작한다").isEqualTo(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        race.close();
        for (String email : emails) {
            jdbc.update("DELETE FROM users WHERE email = ?", email);
        }
        for (Long id : otherAdminIds) {
            jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("A가 B를 해제하고 커밋 전이면 B가 A를 해제하는 요청은 기다렸다가 LastAdminDemotionNotAllowedException이고, 관리자는 A 한 명 남는다")
    void secondDemotionWaitsThenRejected() throws Exception {
        Future<?> first = race.holdFirst(() -> adminUserService.changeRole(adminB, Role.USER, adminA));

        Future<AdminUserResponse> second = race.submit(() -> adminUserService.changeRole(adminA, Role.USER, adminB));

        race.assertBlocked(second);
        race.releaseFirst();
        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(LastAdminDemotionNotAllowedException.class);
        assertThat(adminCount()).isEqualTo(1);
        assertThat(roleOf(adminA)).isEqualTo("ADMIN");
        assertThat(roleOf(adminB)).isEqualTo("USER");
    }

    @Test
    @DisplayName("A·B가 동시에 서로를 해제하면 정확히 한 요청만 성공하고 나머지는 LastAdminDemotionNotAllowedException이며, 관리자는 1명 남는다")
    void simultaneousDemotionsLeaveOneAdmin() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<AdminUserResponse> aDemotesB =
                race.submit(startTogether(start, () -> adminUserService.changeRole(adminB, Role.USER, adminA)));
        Future<AdminUserResponse> bDemotesA =
                race.submit(startTogether(start, () -> adminUserService.changeRole(adminA, Role.USER, adminB)));

        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        int succeeded = 0;
        for (Future<AdminUserResponse> result : List.of(aDemotesB, bDemotesA)) {
            try {
                result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        assertThat(succeeded).isEqualTo(1);
        assertThat(failures).singleElement().isInstanceOf(LastAdminDemotionNotAllowedException.class);
        assertThat(adminCount()).isEqualTo(1);
    }

    private static <T> Callable<T> startTogether(CountDownLatch start, Callable<T> work) {
        return () -> {
            if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("출발 신호를 받지 못했다");
            }
            return work.call();
        };
    }

    private long newAdmin() {
        String email = "race-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        emails.add(email);
        return userService.createAccount(email, PASSWORD, "관리자", Role.ADMIN).id();
    }

    private long adminCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'ADMIN'", Long.class);
        return count == null ? 0 : count;
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
    }
}
