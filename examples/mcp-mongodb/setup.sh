#!/usr/bin/env bash
# Loads a complete, worked MCP example: a collection with an mcp block, an
# mcp-enabled aggregation and change stream on it, and an mcp-enabled GraphQL
# app reading from the same collection.
#
# Usage: ./setup.sh [restheart-url] [admin-user] [admin-password] [gql-apps-collection]
# Defaults match a stock RESTHeart install (restheart-default-config.yml).

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
USER="${2:-admin}"
PASSWORD="${3:-secret}"
GQL_APPS_COLLECTION="${4:-gql-apps}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Creating database 'warehouse'"
curl -sf -X PUT "$BASE_URL/warehouse" -u "$USER:$PASSWORD" -o /dev/null

echo "==> Creating the JSON Schema store and the 'product' schema"
curl -sf -X PUT "$BASE_URL/warehouse/_schemas" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' -d '{}' -o /dev/null
curl -sf -X POST "$BASE_URL/warehouse/_schemas" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' \
  -d @"$DIR/product-schema.json" -o /dev/null

echo "==> Creating the 'inventory' collection: jsonSchema + mcp + aggregation + change stream"
curl -sf -X PUT "$BASE_URL/warehouse/inventory" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' \
  -d @"$DIR/inventory-collection.json" -o /dev/null

echo "==> Seeding a few products"
curl -sf -X POST "$BASE_URL/warehouse/inventory" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' \
  -d '{ "sku": "widget", "item": "widget", "qty": 50, "status": "A" }' -o /dev/null
curl -sf -X POST "$BASE_URL/warehouse/inventory" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' \
  -d '{ "sku": "gadget", "item": "gadget", "qty": 5, "status": "A" }' -o /dev/null
curl -sf -X POST "$BASE_URL/warehouse/inventory" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' \
  -d '{ "sku": "gizmo", "item": "gizmo", "qty": 20, "status": "D" }' -o /dev/null

echo "==> Creating the '$GQL_APPS_COLLECTION' collection and the 'warehouse' GraphQL app"
curl -sf -X PUT "$BASE_URL/$GQL_APPS_COLLECTION" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' -d '{}' -o /dev/null
curl -sf -X POST "$BASE_URL/$GQL_APPS_COLLECTION" -u "$USER:$PASSWORD" -H 'Content-Type: application/json' \
  -d @"$DIR/warehouse-graphql-app.json" -o /dev/null

echo "==> Done. Call list_apis() against $BASE_URL/mcp — you should see:"
echo "    - $BASE_URL/warehouse/inventory (collection)"
echo "    - $BASE_URL/warehouse/inventory/_aggrs/byStatus (aggregation)"
echo "    - $BASE_URL/warehouse/inventory/_streams/lowStock (change-stream)"
echo "    - $BASE_URL/graphql/warehouse (graphql-app)"
