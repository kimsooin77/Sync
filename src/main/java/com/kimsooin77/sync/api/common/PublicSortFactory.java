package com.kimsooin77.sync.api.common;

import org.springframework.data.domain.Sort;

import java.util.Map;

public final class PublicSortFactory {
    private PublicSortFactory() { }

    public static Sort create(String sortBy, String sortDirection, String defaultProperty,
                              Sort.Direction defaultDirection, Map<String, String> properties) {
        String key = sortBy == null || sortBy.isBlank() ? null : sortBy.strip();
        Sort.Direction direction = direction(sortDirection, defaultDirection);
        if (key == null) return Sort.by(defaultDirection, defaultProperty).and(Sort.by(defaultDirection, "id"));
        String property = properties.get(key);
        if (property == null) throw new IllegalArgumentException("Unsupported sortBy");
        Sort sort = Sort.by(direction, property);
        if (!property.equals("id")) sort = sort.and(Sort.by(direction, "id"));
        return sort;
    }

    private static Sort.Direction direction(String value, Sort.Direction fallback) {
        if (value == null || value.isBlank()) return fallback;
        if ("ASC".equalsIgnoreCase(value.strip())) return Sort.Direction.ASC;
        if ("DESC".equalsIgnoreCase(value.strip())) return Sort.Direction.DESC;
        throw new IllegalArgumentException("sortDirection must be ASC or DESC");
    }
}
