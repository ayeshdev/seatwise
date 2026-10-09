package com.seatwise.search.internal;

import java.util.List;

/**
 * The API's {@code WorkshopSearchResult}: the usual page envelope plus
 * {@code searchMode}, {@value #INDEX} or {@value #FALLBACK}, so the UI can say
 * when search is running in basic mode.
 */
public record WorkshopSearchResult(
        List<WorkshopSummaryResponse> items, int page, int size, long totalItems, String searchMode) {

    public static final String INDEX = "index";
    public static final String FALLBACK = "fallback";

    public WorkshopSearchResult {
        items = List.copyOf(items);
    }
}
