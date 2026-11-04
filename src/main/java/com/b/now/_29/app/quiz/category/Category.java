package com.b.now._29.app.quiz.category;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;

    private String name;

    private int sortOrder;

    protected Category() {
        // JPA 전용
    }

    public Category(String slug, String name, int sortOrder) {
        this.slug = slug;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }
}
