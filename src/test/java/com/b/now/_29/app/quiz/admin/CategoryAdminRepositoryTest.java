package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 카테고리 관리자 담당 매핑 (role-owner-category-admin API 계약 {@code CategoryAdmin}, {@code CategoryAdminRepository}).
 *
 * <p>엔티티 {@code quiz.admin.CategoryAdmin(User user, Category category)} — 테이블 {@code category_admin (user_id,
 * category_id)}, 회원과 카테고리는 {@code @ManyToOne(fetch = LAZY)} ({@code QuestionAttempt} 관례). 읽기는 {@code getUser()},
 * {@code getCategory()}.
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class CategoryAdminRepositoryTest {

    @Autowired
    CategoryAdminRepository categoryAdminRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("findAllByUserId는 그 회원의 담당 매핑만 돌려준다")
    void findsByUserId() {
        long manager = saveManager();
        long other = saveManager();
        Category java = saveCategory(10);
        Category spring = saveCategory(20);
        assign(manager, java);
        assign(manager, spring);
        assign(other, java);
        entityManager.clear();

        List<CategoryAdmin> found = categoryAdminRepository.findAllByUserId(manager);

        assertThat(found).extracting(mapping -> mapping.getUser().getId()).containsOnly(manager);
        assertThat(found)
                .extracting(mapping -> mapping.getCategory().getId())
                .containsExactlyInAnyOrder(java.getId(), spring.getId());
    }

    @Test
    @DisplayName("findAllWithCategoryByUserIdIn은 여러 회원의 매핑을 카테고리까지 한 번에 읽는다 (N+1 방지)")
    void findsWithCategoryByUserIds() {
        long first = saveManager();
        long second = saveManager();
        long notAsked = saveManager();
        Category java = saveCategory(10);
        Category spring = saveCategory(20);
        assign(first, java);
        assign(second, spring);
        assign(notAsked, java);
        entityManager.clear();

        List<CategoryAdmin> found = categoryAdminRepository.findAllWithCategoryByUserIdIn(List.of(first, second));

        assertThat(found).extracting(mapping -> mapping.getUser().getId()).containsExactlyInAnyOrder(first, second);
        assertThat(found).allSatisfy(mapping -> assertThat(Hibernate.isInitialized(mapping.getCategory()))
                .as("카테고리를 fetch join으로 함께 읽는다")
                .isTrue());
        assertThat(found)
                .extracting(mapping -> mapping.getCategory().getSlug())
                .containsExactlyInAnyOrder(java.getSlug(), spring.getSlug());
    }

    @Test
    @DisplayName("deleteAllByUserId는 그 회원의 매핑만 지운다")
    void deletesByUserId() {
        long manager = saveManager();
        long other = saveManager();
        Category java = saveCategory(10);
        assign(manager, java);
        assign(other, java);

        categoryAdminRepository.deleteAllByUserId(manager);
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryAdminRepository.findAllByUserId(manager)).isEmpty();
        assertThat(categoryAdminRepository.findAllByUserId(other)).hasSize(1);
    }

    @Test
    @DisplayName("countAdminsWithOnlyCategory는 삭제되지 않은 담당이 그 카테고리 하나뿐인 관리자만 센다 (R10)")
    void countsAdminsWhoManageOnlyThatCategory() {
        Category target = saveCategory(10);
        Category other = saveCategory(20);
        Category deleted = saveCategory(30);
        long onlyTarget = saveManager();
        long targetAndOther = saveManager();
        long targetAndDeleted = saveManager();
        long onlyOther = saveManager();
        assign(onlyTarget, target);
        assign(targetAndOther, target);
        assign(targetAndOther, other);
        assign(targetAndDeleted, target);
        assign(targetAndDeleted, deleted);
        assign(onlyOther, other);
        deleted.delete(0, 0);
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryAdminRepository.countAdminsWithOnlyCategory(target.getId()))
                .as("target만 담당하는 회원과, 나머지 담당이 삭제된 회원")
                .isEqualTo(2);
        assertThat(categoryAdminRepository.countAdminsWithOnlyCategory(other.getId()))
                .as("other만 담당하는 회원")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("findUserIdsByCategoryId는 그 카테고리를 담당하는 회원 id만 돌려준다 (D13)")
    void findsUserIdsByCategoryId() {
        Category target = saveCategory(10);
        Category other = saveCategory(20);
        long first = saveManager();
        long second = saveManager();
        long onlyOther = saveManager();
        assign(first, target);
        assign(second, target);
        assign(second, other);
        assign(onlyOther, other);
        entityManager.clear();

        assertThat(categoryAdminRepository.findUserIdsByCategoryId(target.getId()))
                .containsExactlyInAnyOrder(first, second);
        assertThat(categoryAdminRepository.findUserIdsByCategoryId(
                        saveCategory(30).getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("담당하는 관리자가 없는 카테고리는 0이다 (R10)")
    void countsZeroWithoutAdmins() {
        Category nobody = saveCategory(10);

        assertThat(categoryAdminRepository.countAdminsWithOnlyCategory(nobody.getId()))
                .isZero();
    }

    @Test
    @DisplayName("같은 (회원, 카테고리) 매핑은 복합 유니크 제약으로 두 번 저장할 수 없다 (API 계약)")
    void rejectsDuplicateMapping() {
        long manager = saveManager();
        Category java = saveCategory(10);
        assign(manager, java);

        assertThatThrownBy(() -> assign(manager, java)).isInstanceOf(RuntimeException.class);
    }

    private long saveManager() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User(
                "manager-" + suffix + "@example.com",
                "$2a$10$hash",
                Role.CATEGORY_ADMIN,
                new Nickname("m" + suffix),
                1);
        entityManager.persist(user);
        entityManager.flush();
        return user.getId();
    }

    private Category saveCategory(int sortOrder) {
        String slug = "ca-" + UUID.randomUUID().toString().substring(0, 8);
        Category category = new Category(slug, slug, sortOrder);
        entityManager.persist(category);
        entityManager.flush();
        return category;
    }

    private void assign(long userId, Category category) {
        entityManager.persist(new CategoryAdmin(
                entityManager.getReference(User.class, userId),
                entityManager.getReference(Category.class, category.getId())));
        entityManager.flush();
    }
}
