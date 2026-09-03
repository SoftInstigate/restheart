# MCP over MongoDB & GraphQL — a worked example

Loads one small "warehouse" domain that exercises all four kinds `restheart-ai`'s MCP server exposes: a **collection**, an **aggregation**, a **change stream**, and a **GraphQL app** — all pointed at the same data, all opted into MCP.

See the [MCP Server](https://restheart.org/docs/ai/mcp) docs for the full reference this example follows, and the [MCP Server Tutorial](https://restheart.org/docs/ai/mcp-tutorial) for a step-by-step walkthrough of the same ideas.

## Files

| File | What it is |
|---|---|
| `product-schema.json` | JSON Schema for a product document, loaded into `warehouse/_schemas` |
| `inventory-collection.json` | The `inventory` collection's metadata: `jsonSchema` (pointer to the schema above), an `mcp` block, an `mcp`-enabled aggregation (`byStatus`), and an `mcp`-enabled change stream (`lowStock`) |
| `warehouse-graphql-app.json` | A GraphQL app document (for your `gql-apps` collection) reading from `inventory`, with its own `mcp` block |
| `setup.sh` | Loads everything above into a running RESTHeart via its REST API |

## Run it

```bash
./setup.sh [restheart-url] [admin-user] [admin-password] [gql-apps-collection]
# defaults: http://localhost:8080 admin secret gql-apps
```

The last argument matters only if your deployment overrides `graphql.collection` away from the shipped default (`gql-apps`, hyphenated — see `restheart-default-config.yml`).

## What you get

Call `list_apis()` against `/mcp` and you should see all four resources:

```jsonc
{
  "resources": [
    { "uri": ".../warehouse/inventory", "kind": "collection", "description": "Product inventory." },
    { "uri": ".../warehouse/inventory/_aggrs/byStatus", "kind": "aggregation", "description": "Total quantity by item, for a given status." },
    { "uri": ".../warehouse/inventory/_streams/lowStock", "kind": "change-stream", "description": "Low-stock alerts." },
    { "uri": ".../graphql/warehouse", "kind": "graphql-app", "description": "Query the warehouse inventory via GraphQL." }
  ]
}
```

From there, `list_apis(resource: "...")` on any one of them shows its full context (actions, params, `body_schema`, examples), and `how_to_call` composes a ready-to-run descriptor for each — see the docs linked above for what those look like and how to execute them.
