package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 카테고리 관리자의 담당 카테고리. (회원, 카테고리) 조합은 유일하다. */
@Entity
public class CategoryAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    protected CategoryAdmin() {
        // JPA 전용
    }

    public CategoryAdmin(User user, Category category) {
        this.user = user;
        this.category = category;
    }

    public User getUser() {
        return user;
    }

    public Category getCategory() {
        return category;
    }
}
