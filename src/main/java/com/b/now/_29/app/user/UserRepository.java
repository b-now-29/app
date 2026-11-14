package com.b.now._29.app.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByOrderByIdAsc();

    /** 토큰 확인용으로 한 컬럼만 읽는다. 없는 회원은 empty. */
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") long id);

    /** 그 닉네임의 가장 큰 번호. 쓰는 회원이 없으면 empty. */
    @Query("select max(u.nicknameTag.tag) from User u where u.nicknameTag.nickname = :nickname")
    Optional<Integer> findMaxTagByNickname(@Param("nickname") String nickname);
}
