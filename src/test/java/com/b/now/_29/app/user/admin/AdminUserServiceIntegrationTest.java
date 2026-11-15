package com.b.now._29.app.user.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.TestOwner;
import com.b.now._29.app.user.OwnerRoleChangeNotAllowedException;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 역할과 담당 카테고리를 한 요청으로 함께 바꾸고, 회원 목록·상세에 담당 카테고리를 담는지 실제 트랜잭션과 DB로 확인한다
 * (role-owner-category-admin R2, R9, API 계약).
 *
 * <ul>
 *   <li>{@code AdminUserService.changeRole(long id, Role role, List<String> categorySlugs)}: 담당을 통째로 바꾼다. 실패하면 역할과
 *       담당 모두 그대로다
 *   <li>{@code AdminUserService.getUser(long id)}, {@code getUsers()}: {@code categorySlugs}는 삭제되지 않은 담당 카테고리, 카테고리
 *       표시 순서 ({@code sortOrder})
 * </ul>
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@Import(TestcontainersConfiguration.class)
class AdminUserServiceIntegrationTest {

    private static final String PASSWORD = "member1234!";

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> emails = new ArrayList<>();
    private final List<String> slugs = new ArrayList<>();

    private long memberId;
    private String first;
    private String second;

    @BeforeEach
    void setUp() {
        memberId = newMember();
        // 표시 순서는 second가 앞이다. 응답의 categorySlugs가 요청 순서가 아니라 sortOrder 순인지 본다
        first = newCategory(9002);
        second = newCategory(9001);
    }

    @AfterEach
    void tearDown() {
        for (String email : emails) {
            jdbc.update("DELETE FROM category_admin WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbc.update("DELETE FROM users WHERE email = ?", email);
        }
        for (String slug : slugs) {
            jdbc.update(
                    "DELETE FROM category_admin WHERE category_id IN (SELECT id FROM category WHERE slug = ?)", slug);
            jdbc.update("DELETE FROM category WHERE slug = ?", slug);
        }
    }

    @Test
    @DisplayName("USER를 CATEGORY_ADMIN으로 바꾸면서 담당 카테고리를 함께 정하고, 응답의 categorySlugs는 카테고리 표시 순서다 (R9)")
    void promotesWithCategories() {
        AdminUserResponse response = adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, second));

        assertThat(response.role()).isEqualTo(Role.CATEGORY_ADMIN);
        assertThat(response.categorySlugs()).containsExactly(second, first);
        assertThat(roleOf(memberId)).isEqualTo("CATEGORY_ADMIN");
        assertThat(managedSlugs(memberId)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("같은 slug가 여러 번 오면 하나로 보고 매핑을 하나만 만든다 (API 계약)")
    void duplicateSlugsMakeOneMapping() {
        AdminUserResponse response =
                adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, first, second, first));

        assertThat(response.categorySlugs()).containsExactly(second, first);
        assertThat(managedSlugs(memberId)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("다시 바꾸면 담당 카테고리를 통째로 바꾼다. 빠진 카테고리의 담당은 없어진다 (API 계약)")
    void replacesCategories() {
        adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, second));

        AdminUserResponse response = adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first));

        assertThat(response.categorySlugs()).containsExactly(first);
        assertThat(managedSlugs(memberId)).containsExactly(first);
    }

    @Test
    @DisplayName("CATEGORY_ADMIN을 USER로 바꾸면 담당 카테고리가 모두 없어진다 (R9)")
    void demotionClearsCategories() {
        adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, second));

        AdminUserResponse response = adminUserService.changeRole(memberId, Role.USER, List.of());

        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.categorySlugs()).isEmpty();
        assertThat(roleOf(memberId)).isEqualTo("USER");
        assertThat(managedSlugs(memberId)).isEmpty();
    }

    @Test
    @DisplayName("없는 slug가 섞이면 CategoryNotFoundException이고 역할과 담당이 모두 그대로다 (API 계약)")
    void unknownSlugChangesNothing() {
        adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first));

        assertThatThrownBy(() -> adminUserService.changeRole(
                        memberId, Role.CATEGORY_ADMIN, List.of(second, "it-nope-" + UUID.randomUUID())))
                .isInstanceOf(CategoryNotFoundException.class);

        assertThat(roleOf(memberId)).isEqualTo("CATEGORY_ADMIN");
        assertThat(managedSlugs(memberId)).containsExactly(first);
    }

    @Test
    @DisplayName("삭제된 카테고리 slug도 CategoryNotFoundException이고 USER는 그대로 USER다 (API 계약)")
    void deletedCategorySlugIsNotFound() {
        jdbc.update("UPDATE category SET deleted_at = now() WHERE slug = ?", second);

        assertThatThrownBy(() -> adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, second)))
                .isInstanceOf(CategoryNotFoundException.class);

        assertThat(roleOf(memberId)).isEqualTo("USER");
        assertThat(managedSlugs(memberId)).isEmpty();
    }

    @Test
    @DisplayName("대상이 OWNER면 OwnerRoleChangeNotAllowedException이고 OWNER 그대로이며 담당도 생기지 않는다 (R2)")
    void ownerCannotBeChanged() {
        long ownerId = TestOwner.id(jdbc, userService);

        assertThatThrownBy(() -> adminUserService.changeRole(ownerId, Role.CATEGORY_ADMIN, List.of(first)))
                .isInstanceOf(OwnerRoleChangeNotAllowedException.class);
        assertThatThrownBy(() -> adminUserService.changeRole(ownerId, Role.USER, List.of()))
                .isInstanceOf(OwnerRoleChangeNotAllowedException.class);

        assertThat(roleOf(ownerId)).isEqualTo("OWNER");
        assertThat(managedSlugs(ownerId)).isEmpty();
    }

    @Test
    @DisplayName("없는 회원이면 AdminUserNotFoundException이다")
    void unknownUser() {
        assertThatThrownBy(() -> adminUserService.changeRole(Long.MAX_VALUE, Role.USER, List.of()))
                .isInstanceOf(AdminUserNotFoundException.class);
    }

    // 조회

    @Test
    @DisplayName("회원 상세는 삭제되지 않은 담당 카테고리만 표시 순서로 담는다 (API 계약)")
    void getUserListsActiveCategoriesInSortOrder() {
        String third = newCategory(9000);
        adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, second, third));
        jdbc.update("UPDATE category SET deleted_at = now() WHERE slug = ?", third);

        AdminUserResponse response = adminUserService.getUser(memberId);

        assertThat(response.id()).isEqualTo(memberId);
        assertThat(response.role()).isEqualTo(Role.CATEGORY_ADMIN);
        assertThat(response.categorySlugs()).containsExactly(second, first);
    }

    @Test
    @DisplayName("없는 회원의 상세는 AdminUserNotFoundException이다 (API 계약)")
    void getUnknownUser() {
        assertThatThrownBy(() -> adminUserService.getUser(Long.MAX_VALUE))
                .isInstanceOf(AdminUserNotFoundException.class);
    }

    @Test
    @DisplayName("회원 목록의 각 회원도 담당 카테고리를 담고, 담당이 없는 회원은 빈 목록이다 (API 계약)")
    void getUsersIncludesCategories() {
        long other = newMember();
        adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(first, second));

        List<AdminUserResponse> users = adminUserService.getUsers();

        assertThat(users)
                .filteredOn(user -> user.id() == memberId)
                .singleElement()
                .extracting(AdminUserResponse::categorySlugs)
                .isEqualTo(List.of(second, first));
        assertThat(users)
                .filteredOn(user -> user.id() == other)
                .singleElement()
                .extracting(AdminUserResponse::categorySlugs)
                .isEqualTo(List.of());
    }

    private long newMember() {
        String email = "role-it-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        emails.add(email);
        return userService.createAccount(email, PASSWORD, "역할테스트", Role.USER).id();
    }

    private String newCategory(int sortOrder) {
        String slug = "role-it-" + UUID.randomUUID().toString().substring(0, 8);
        slugs.add(slug);
        jdbc.update("INSERT INTO category (slug, name, sort_order) VALUES (?, ?, ?)", slug, slug, sortOrder);
        return slug;
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
    }

    private List<String> managedSlugs(long userId) {
        return jdbc.queryForList(
                "SELECT c.slug FROM category_admin ca JOIN category c ON c.id = ca.category_id WHERE ca.user_id = ?",
                String.class,
                userId);
    }
}
