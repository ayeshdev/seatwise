#!/bin/sh
# Writes /config.json from environment variables so one image runs in every environment.
set -eu

# Escape backslashes and double quotes so env values cannot break the JSON.
json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

IDP_URL=$(json_escape "${SEATWISE_IDP_URL:-http://localhost:8281}")
REALM=$(json_escape "${SEATWISE_REALM:-seatwise}")
CLIENT_ID=$(json_escape "${SEATWISE_CLIENT_ID:-seatwise-desk}")

cat > /usr/share/nginx/html/config.json <<EOF
{
  "idpUrl": "${IDP_URL}",
  "realm": "${REALM}",
  "clientId": "${CLIENT_ID}",
  "apiBase": "/api"
}
EOF

echo "40-write-runtime-config: wrote /usr/share/nginx/html/config.json"
