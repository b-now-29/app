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

    /** 그 닉네임의 가장 큰 번호. 쓰는 회원이 없으면 empty. */
    @Query("select max(u.nicknameTag.tag) from User u where u.nicknameTag.nickname = :nickname")
    Optional<Integer> findMaxTagByNickname(@Param("nickname") String nickname);
}
