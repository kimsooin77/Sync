package com.kimsooin77.sync.api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {
    private PageRequestFactory() { }

    public static PageRequest create(int page, int size, String... sort) {
        validate(page, size);
        Sort ordering = Sort.by(sort[0]).ascending();
        for (int i = 1; i < sort.length; i++) ordering = ordering.and(Sort.by(sort[i]).ascending());
        return PageRequest.of(page, size, ordering);
    }

    public static PageRequest create(int page, int size, Sort ordering) {
        validate(page, size);
        return PageRequest.of(page, size, ordering);
    }

    private static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
    }
}
