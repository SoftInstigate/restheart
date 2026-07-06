# RESTHeart Helm Chart

A Helm chart for deploying [RESTHeart](https://restheart.org), a Java backend framework for REST, GraphQL, and WebSocket APIs with MongoDB.

## Prerequisites

- Kubernetes 1.22+
- Helm 3.x
- MongoDB instance (external or deployed separately)

## Install

From GHCR (OCI registry):

```bash
helm install my-restheart oci://ghcr.io/softinstigate/restheart --version 9.5.0
```

Or from the chart directory:

```bash
helm install my-restheart . -f my-values.yaml
```

## Quick Start

Create a `my-values.yaml`:

```yaml
restHeartConfiguration:
  mongo-uri: "mongodb://mongo:27017"
  authenticators:
    mongoRealmAuthenticator:
      create-user-document: '{"_id": "admin", "password": "$2a$12$lZiMMNJ6pkyg4uq/I1cF5uxzUbU25aXHtg7W7sD2ED7DG1wzUoo6u", "roles": ["admin"]}'
```

Then install:

```bash
helm install my-restheart ./chart -f my-values.yaml
```

## Configuration

### RESTHeart Application Config

The `restHeartConfiguration` section maps directly to `restheart.yml`. See [RESTHEART-CONFIG.md](./RESTHEART-CONFIG.md) for the full reference.

### Key Helm Values

| Value | Default | Description |
|-------|---------|-------------|
| `replicaCount` | `1` | Number of replicas |
| `image.repository` | `softinstigate/restheart` | Docker image |
| `image.tag` | Chart appVersion | Image tag |
| `containerPort` | `8080` | Container port |
| `service.type` | `ClusterIP` | Service type |
| `service.port` | `80` | Service port |
| `ingress.enabled` | `false` | Enable ingress |
| `autoscaling.enabled` | `false` | Enable HPA |
| `podDisruptionBudget.enabled` | `false` | Enable PDB |
| `networkPolicy.enabled` | `false` | Enable network policy |
| `waitForMongo.enabled` | `false` | Init container to wait for MongoDB |

### MongoDB Init Container

If your MongoDB is deployed in the same cluster, enable the init container to prevent crash loops:

```yaml
waitForMongo:
  enabled: true
  host: "mongo.mongodb.svc.cluster.local"
  port: 27017
```

### External Configuration

To use a pre-existing Secret instead of generating one from `restHeartConfiguration`:

```yaml
useExternalConfig: true
externalConfig:
  name: "my-restheart-config"
  key: "restheart.yml"
```

### Sidecars and Extra Volumes

```yaml
extraContainers:
  - name: log-shipper
    image: fluent/fluent-bit:latest
    volumeMounts:
      - name: shared-logs
        mountPath: /var/log

extraVolumes:
  - name: shared-logs
    emptyDir: {}

extraVolumeMounts:
  - name: keystore
    mountPath: /opt/restheart/etc/keystore
    readOnly: true
```

### Security Defaults

The chart ships with secure defaults:

```yaml
podSecurityContext:
  runAsNonRoot: true
  fsGroup: 1000

securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  runAsNonRoot: true
  runAsUser: 1000
```

To override (e.g. for development):

```yaml
securityContext: {}
podSecurityContext: {}
```

### Network Policy

Restrict traffic to only the ingress controller and MongoDB:

```yaml
networkPolicy:
  enabled: true
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-nginx
      ports:
        - port: 8080
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: mongodb
      ports:
        - port: 27017
```

## Upgrading

```bash
helm upgrade my-restheart ./chart -f my-values.yaml
```

The chart includes a config checksum annotation. Changes to `restHeartConfiguration` trigger a rolling restart automatically.

## Running Tests

```bash
helm test my-restheart
```

## Uninstalling

```bash
helm uninstall my-restheart
```

## Chart Structure

```
chart/
├── Chart.yaml                    # Chart metadata
├── values.yaml                   # Default values
├── RESTHEART-CONFIG.md           # RESTHeart config reference
├── README.md                     # This file
├── .helmignore
└── templates/
    ├── NOTES.txt                 # Post-install notes
    ├── _helpers.tpl              # Template helpers
    ├── deployment.yaml           # Main deployment
    ├── service.yaml              # Service
    ├── serviceaccount.yaml       # Service account
    ├── secret.yaml               # Config secret (when not external)
    ├── ingress.yaml              # Ingress
    ├── hpa.yaml                  # Horizontal pod autoscaler
    ├── pdb.yaml                  # Pod disruption budget
    ├── networkpolicy.yaml        # Network policy
    └── tests/
        └── test-connection.yaml  # Helm test
```
