/**
 * Workshop search: a derived Meilisearch index kept in sync from workshop and
 * registration events. PostgreSQL stays the source of truth.
 */
@ApplicationModule(displayName = "Workshop search", allowedDependencies = {"workshops", "registrations", "common"})
package com.seatwise.search;

import org.springframework.modulith.ApplicationModule;
