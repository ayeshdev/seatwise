package com.seatwise.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The one paging envelope of the API ({@code {items, page, size, totalItems}}).
 * Spring's {@code Page} is never serialized directly: its JSON shape is an
 * implementation detail that has changed between versions.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <X, T> PageResponse<T> from(Page<X> page, Function<? super X, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
