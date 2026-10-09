package com.seatwise.search.internal;

import com.meilisearch.sdk.model.Pagination;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TypoTolerance;
import com.seatwise.workshops.WorkshopView;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code workshops} index: its settings and the document shape
 * (architecture section 7a, "Index workshops"). Times are epoch seconds so
 * they can be range-filtered; seat counts are copied so {@code hasSeats} and
 * the OPEN/FULL filters can run in the index. The list page itself is always
 * hydrated from PostgreSQL, so these copies only decide which rows match.
 */
final class WorkshopDocuments {

    static final String INDEX = "workshops";
    static final String PRIMARY_KEY = "id";

    private WorkshopDocuments() {}

    static Settings settings() {
        Settings settings = new Settings();
        // In ranking order: a code hit beats a title hit beats a description hit.
        settings.setSearchableAttributes(new String[] {"code", "title", "instructor", "locationName", "description"});
        settings.setFilterableAttributes(new String[] {"startsAt", "endsAt", "lifecycle", "locationId", "seatsLeft"});
        settings.setSortableAttributes(new String[] {"startsAt", "title"});
        // enabled must be set explicitly: the SDK serializes the primitive,
        // and false would switch typo tolerance off everywhere.
        settings.setTypoTolerance(new TypoTolerance()
                .setEnabled(true)
                .setDisableOnAttributes(new String[] {"code"}));
        settings.setPagination(new Pagination(1000));
        return settings;
    }

    static Map<String, Object> document(WorkshopView w) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", w.id().toString());
        doc.put("code", w.code());
        doc.put("title", w.title());
        doc.put("instructor", w.instructor());
        doc.put("description", w.description());
        doc.put("locationId", w.location() == null ? null : w.location().id().toString());
        doc.put("locationName", w.location() == null ? null : w.location().name());
        doc.put("startsAt", w.startsAt().getEpochSecond());
        doc.put("endsAt", w.endsAt().getEpochSecond());
        doc.put("lifecycle", w.lifecycle().name());
        doc.put("capacity", w.capacity());
        doc.put("seatsTaken", w.seatsTaken());
        doc.put("seatsLeft", w.seatsLeft());
        return doc;
    }
}
