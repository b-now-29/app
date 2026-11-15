package com.b.now._29.app.quiz.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;

    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private int sortOrder;

    /** soft delete 시각. null이면 삭제되지 않은 카테고리다. */
    private Instant deletedAt;

    protected Category() {
        // JPA 전용
    }

    public Category(String slug, String name, int sortOrder) {
        this(slug, name, null, sortOrder);
    }

    public Category(String slug, String name, String description, int sortOrder) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    /** slug는 바꾸지 않는다. */
    public void update(String name, String description, int sortOrder) {
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    /**
     * 삭제 처리한다. 삭제되지 않은 문제가 남아 있거나 이 카테고리만 담당하는 관리자가 있으면 거부한다.
     *
     * @param remainingQuestionCount 삭제되지 않은 문제 수 (category는 question을 참조하지 않으므로 값으로 받는다)
     * @param soleAdminCount 삭제되지 않은 담당이 이 카테고리 하나뿐인 관리자 수
     */
    public void delete(long remainingQuestionCount, long soleAdminCount) {
        if (remainingQuestionCount > 0) {
            throw new CategoryNotEmptyException(slug);
        }
        if (soleAdminCount > 0) {
            throw new CategoryHasSoleAdminException();
        }
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
