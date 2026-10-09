# Production identity provider image: Keycloak pre-built for PostgreSQL with the
# Seatwise realm baked in. Local Compose runs the stock image in dev mode with
# the same realm file mounted, so both environments import identical settings.
ARG KEYCLOAK_VERSION=26.4

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS builder
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# Redirect URIs and the provisioner secret are ${...} placeholders resolved from
# the container environment at import time, so no secret lives in the image.
COPY seatwise-realm.json /opt/keycloak/data/import/seatwise-realm.json
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
