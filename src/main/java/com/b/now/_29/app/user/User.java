package com.b.now._29.app.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "users")
public class User {

    public static final int INITIAL_TOKEN_VERSION = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** 가입 시각 저장 전에 가입한 회원은 null이다 (D4). */
    @Column(name = "created_at")
    private Instant createdAt;

    @Embedded
    private NicknameTag nicknameTag;

    /** 로그아웃할 때 올라가고, 토큰에 담긴 값과 다르면 그 토큰은 무효다. */
    @Column(name = "token_version")
    private int tokenVersion;

    protected User() {
        // JPA 전용
    }

    public User(String email, String passwordHash, Nickname nickname, int tag) {
        this(email, passwordHash, Role.USER, nickname, tag);
    }

    public User(String email, String passwordHash, Role role, Nickname nickname, int tag) {
        this.nicknameTag = new NicknameTag(nickname, tag);
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
        this.tokenVersion = INITIAL_TOKEN_VERSION;
    }

    /** 이미 발급한 모든 토큰을 무효로 한다. {@code @DynamicUpdate}로 이 컬럼만 UPDATE된다. */
    public void revokeTokens() {
        tokenVersion++;
    }

    /**
     * 지금 해시가 비교에 쓴 해시와 같을 때만 바꾸고 (D5), 바뀌면 이미 발급한 토큰은 모두 무효다 (D4). {@code @DynamicUpdate}로 두 컬럼만
     * UPDATE된다.
     */
    public void changePassword(String expectedHash, String newHash) {
        if (!passwordHash.equals(expectedHash)) {
            throw new WrongCurrentPasswordException();
        }
        this.passwordHash = newHash;
        revokeTokens();
    }

    /** 지금 토큰 버전으로 토큰을 발급할 때 쓰는 주체. */
    public TokenSubject tokenSubject() {
        return new TokenSubject(id, tokenVersion);
    }

    /** 지금 닉네임과 같은지 본다. 같으면 번호를 포함해 아무것도 바꾸지 않는다 (R5, D11). */
    public boolean hasNickname(Nickname other) {
        return other.value().equals(nicknameTag.nickname());
    }

    /** 닉네임과 번호를 바꾼다. {@code @DynamicUpdate}로 이 두 컬럼만 UPDATE된다 (D12). */
    public void changeNickname(Nickname nickname, int tag) {
        this.nicknameTag = new NicknameTag(nickname, tag);
    }

    /** 자기 자신을 USER로 바꾸는 것은 막는다. 같은 role로 바꾸면 변화 없이 받아들인다 (D5). */
    public void changeRole(Role newRole, long actorId) {
        if (newRole == Role.USER && id != null && id == actorId) {
            throw new SelfDemotionNotAllowedException(actorId);
        }
        this.role = newRole;
    }

    /** 이메일은 앞뒤 공백을 지우고 소문자로 저장하고 비교한다. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public NicknameTag getNicknameTag() {
        return nicknameTag;
    }

    public String getNickname() {
        return nicknameTag.nickname();
    }

    public int getTag() {
        return nicknameTag.tag();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
