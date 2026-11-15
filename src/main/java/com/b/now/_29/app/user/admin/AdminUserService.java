package com.b.now._29.app.user.admin;

import com.b.now._29.app.quiz.admin.CategoryAdmin;
import com.b.now._29.app.quiz.admin.CategoryAdminRepository;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import com.b.now._29.app.user.UserRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private static final Comparator<Category> DISPLAY_ORDER =
            Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getId);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryAdminRepository categoryAdminRepository;

    public AdminUserService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            CategoryAdminRepository categoryAdminRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.categoryAdminRepository = categoryAdminRepository;
    }

    public List<AdminUserResponse> getUsers() {
        List<User> users = userRepository.findAllByOrderByIdAsc();
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, List<String>> slugsByUserId = categoryAdminRepository.findAllWithCategoryByUserIdIn(ids).stream()
                .filter(mapping -> !mapping.getCategory().isDeleted())
                .sorted(Comparator.comparing(CategoryAdmin::getCategory, DISPLAY_ORDER))
                .collect(Collectors.groupingBy(
                        mapping -> mapping.getUser().getId(),
                        Collectors.mapping(mapping -> mapping.getCategory().getSlug(), Collectors.toList())));
        return users.stream()
                .map(user -> AdminUserResponse.from(user, slugsByUserId.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    public AdminUserResponse getUser(long id) {
        User user = findUser(id);
        return AdminUserResponse.from(user, assignedSlugs(user.getId()));
    }

    /**
     * 역할과 담당 카테고리를 통째로 바꾼다. 담당 카테고리는 공유 잠금으로 읽어 카테고리 삭제와 직렬화한다. slug는 정렬해서 잠가 같은 순서로
     * 잠그는 요청끼리 교착 상태가 생기지 않게 한다.
     */
    @Transactional
    public AdminUserResponse changeRole(long id, Role role, List<String> categorySlugs) {
        User user = findUser(id);
        user.changeRole(role);
        List<Category> categories = new TreeSet<>(categorySlugs)
                .stream()
                        .map(slug -> categoryRepository
                                .findWithShareLockBySlugAndDeletedAtIsNull(slug)
                                .orElseThrow(() -> new CategoryNotFoundException(slug)))
                        .toList();
        categoryAdminRepository.deleteAllByUserId(id);
        // 같은 (회원, 카테고리)를 지우고 다시 넣을 때 INSERT가 DELETE보다 먼저 나가 유니크 제약을 어기지 않도록 DELETE를 먼저 보낸다
        categoryAdminRepository.flush();
        categoryAdminRepository.saveAll(categories.stream()
                .map(category -> new CategoryAdmin(user, category))
                .toList());
        return AdminUserResponse.from(user, slugsInDisplayOrder(categories));
    }

    private User findUser(long id) {
        return userRepository.findById(id).orElseThrow(() -> new AdminUserNotFoundException(id));
    }

    private List<String> assignedSlugs(long userId) {
        Collection<Category> categories = categoryAdminRepository.findAllByUserId(userId).stream()
                .map(CategoryAdmin::getCategory)
                .filter(category -> !category.isDeleted())
                .toList();
        return slugsInDisplayOrder(categories);
    }

    private static List<String> slugsInDisplayOrder(Collection<Category> categories) {
        return categories.stream().sorted(DISPLAY_ORDER).map(Category::getSlug).toList();
    }
}
