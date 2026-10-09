package com.seatwise.search.internal;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.model.Key;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The two Meilisearch clients (architecture section 7a, "Security"). The
 * master-key client only does administration: creating the index, applying
 * its settings and deriving the runtime key. Everything else (searching,
 * writing documents, waiting for tasks) uses a key scoped to the
 * {@code workshops} index, so a leak of that key can't touch other indexes,
 * create keys or dump the instance.
 *
 * <p>The runtime key has a fixed uid, so restarts find the key they created
 * last time instead of piling up new ones; its value is derived by
 * Meilisearch from the uid and the master key, so rotating the master key
 * rotates it too. Derivation is lazy and retried: if Meilisearch is down at
 * startup, the first successful call creates it.
 */
class MeilisearchClients {

    private static final Logger log = LoggerFactory.getLogger(MeilisearchClients.class);

    static final String RUNTIME_KEY_UID = "6d3e9a52-7c1b-4f0e-8a24-5b9d0c61e7f3";
    static final Set<String> RUNTIME_ACTIONS = Set.of("search", "documents.*", "settings.*", "tasks.get");

    private final String url;
    private final Client admin;
    private volatile Client runtime;

    MeilisearchClients(String url, String masterKey) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "seatwise.search.url (SEATWISE_SEARCH_URL) is not set; set it or disable the index with "
                            + "seatwise.search.enabled=false");
        }
        this.url = url;
        this.admin = new Client(new Config(url, masterKey));
    }

    /** Master-key client: index creation, settings and key management only. */
    Client admin() {
        return admin;
    }

    /** Scoped client for everything else; derives the key on first use. */
    Client runtime() {
        Client current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (runtime == null) {
                runtime = deriveRuntimeClient();
            }
            return runtime;
        }
    }

    private Client deriveRuntimeClient() {
        try {
            Key key = findRuntimeKey();
            if (key != null && !hasExpectedScope(key)) {
                admin.deleteKey(RUNTIME_KEY_UID);
                key = null;
            }
            if (key == null) {
                key = admin.createKey(runtimeKeyRequest());
            }
            return new Client(new Config(url, key.getKey()));
        } catch (MeilisearchApiException e) {
            if (!"missing_master_key".equals(e.getCode())) {
                throw e;
            }
            // An instance started without a master key has no key management
            // (and no authentication to scope): nothing to derive.
            log.warn("Meilisearch runs without a master key; no scoped key can be derived");
            return admin;
        }
    }

    private Key findRuntimeKey() {
        try {
            return admin.getKey(RUNTIME_KEY_UID);
        } catch (MeilisearchApiException e) {
            if ("api_key_not_found".equals(e.getCode())) {
                return null;
            }
            throw e;
        }
    }

    private static boolean hasExpectedScope(Key key) {
        return key.getActions() != null
                && Set.copyOf(Arrays.asList(key.getActions())).equals(RUNTIME_ACTIONS)
                && key.getIndexes() != null
                && Arrays.equals(key.getIndexes(), new String[] {WorkshopDocuments.INDEX})
                && key.getExpiresAt() == null;
    }

    private static Key runtimeKeyRequest() {
        Key key = new Key()
                .setName("seatwise-api runtime")
                .setDescription("Search and document writes on the workshops index; derived at API startup")
                .setActions(RUNTIME_ACTIONS.stream().sorted().toArray(String[]::new))
                .setIndexes(new String[] {WorkshopDocuments.INDEX})
                .setExpiresAt(null);
        key.setUid(RUNTIME_KEY_UID);
        return key;
    }
}
