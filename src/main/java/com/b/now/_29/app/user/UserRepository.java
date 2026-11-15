package com.b.now._29.app.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByOrderByIdAsc();

    /**
     * 그 역할의 회원 행을 id 순서로 모두 잠근다. 관리자 행을 같은 순서로 잠그므로 역할 변경이 직렬화되고 교착 상태가 생기지
     * 않는다. 기다렸던 트랜잭션은 WHERE를 다시 평가하므로, 이미 USER가 된 행은 결과에서 빠진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findWithWriteLockByRoleOrderByIdAsc(Role role);

    /** 토큰 확인용으로 한 컬럼만 읽는다. 없는 회원은 empty. */
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") long id);

    /** 그 닉네임의 가장 큰 번호. 쓰는 회원이 없으면 empty. */
    @Query("select max(u.nicknameTag.tag) from User u where u.nicknameTag.nickname = :nickname")
    Optional<Integer> findMaxTagByNickname(@Param("nickname") String nickname);
}
