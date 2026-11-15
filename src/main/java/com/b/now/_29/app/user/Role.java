package com.b.now._29.app.user;

public enum Role {
    USER,
    CATEGORY_ADMIN,
    OWNER;

    /** USER가 아니면 관리자다 (카테고리 관리자와 운영자). */
    public boolean isStaff() {
        return this != USER;
    }

    public boolean isOwner() {
        return this == OWNER;
    }

    /** 담당 카테고리가 1개 이상 있어야 하는 역할이다 (R9). */
    public boolean requiresCategories() {
        return this == CATEGORY_ADMIN;
    }

    /** API로 줄 수 있는 역할이다. 운영자는 줄 수 없다 (R2). */
    public boolean isAssignable() {
        return this == USER || this == CATEGORY_ADMIN;
    }
}
