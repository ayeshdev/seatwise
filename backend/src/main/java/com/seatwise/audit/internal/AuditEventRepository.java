package com.seatwise.audit.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain JDBC over {@code audit_event}: an insert and a filtered, newest-first
 * read. There is deliberately no update or delete (a trigger refuses both).
 * JDBC rather than JPA because the table is append-only and its {@code changes}
 * column is jsonb; the statements still run on the caller's transaction's
 * connection, so an insert rolls back with the change it records.
 */
@Repository
class AuditEventRepository {

    private static final TypeReference<Map<String, AuditChange>> CHANGES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditEventRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** The filter of one query; {@code types} is never empty, the other fields are optional. */
    record Filter(Set<AuditEntityType> types, UUID entityId, UUID workshopId, Instant from, Instant toExclusive) {}

    void insert(AuditEventRow row) {
        jdbc.sql("""
                        INSERT INTO audit_event (occurred_at, actor_id, entity_type, entity_id, workshop_id,
                                                 action, summary, changes)
                        VALUES (:occurredAt, :actorId, :entityType, :entityId, :workshopId,
                                :action, :summary, CAST(:changes AS jsonb))""")
                .param("occurredAt", utc(row.occurredAt()))
                .param("actorId", row.actorId())
                .param("entityType", row.entityType().name())
                .param("entityId", row.entityId())
                .param("workshopId", row.workshopId())
                .param("action", row.action().name())
                .param("summary", row.summary())
                .param("changes", json.writeValueAsString(row.changes()))
                .update();
    }

    /** Newest first; events of the same instant newest-inserted first, so the order is stable. */
    List<AuditEventRow> find(Filter filter, long offset, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = where(filter, params);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql("""
                        SELECT id, occurred_at, actor_id, entity_type, entity_id, workshop_id, action, summary,
                               changes::text AS changes
                          FROM audit_event
                        """ + where + """
                         ORDER BY occurred_at DESC, id DESC
                         LIMIT :limit OFFSET :offset""")
                .params(params)
                .query(this::map)
                .list();
    }

    long count(Filter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = where(filter, params);
        return jdbc.sql("SELECT count(*) FROM audit_event " + where)
                .params(params)
                .query(Long.class)
                .single();
    }

    private static String where(Filter filter, Map<String, Object> params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("entity_type IN (:types)");
        params.put("types", filter.types().stream().map(Enum::name).sorted().toList());
        if (filter.entityId() != null) {
            clauses.add("entity_id = :entityId");
            params.put("entityId", filter.entityId());
        }
        if (filter.workshopId() != null) {
            clauses.add("workshop_id = :workshopId");
            params.put("workshopId", filter.workshopId());
        }
        if (filter.from() != null) {
            clauses.add("occurred_at >= :from");
            params.put("from", utc(filter.from()));
        }
        if (filter.toExclusive() != null) {
            clauses.add("occurred_at < :to");
            params.put("to", utc(filter.toExclusive()));
        }
        return " WHERE " + String.join(" AND ", clauses) + "\n";
    }

    private AuditEventRow map(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEventRow(
                rs.getLong("id"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("actor_id", UUID.class),
                AuditEntityType.valueOf(rs.getString("entity_type")),
                rs.getObject("entity_id", UUID.class),
                rs.getObject("workshop_id", UUID.class),
                AuditAction.valueOf(rs.getString("action")),
                rs.getString("summary"),
                json.readValue(rs.getString("changes"), CHANGES));
    }

    // The PostgreSQL driver binds OffsetDateTime to timestamptz; it has no Instant binding.
    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
