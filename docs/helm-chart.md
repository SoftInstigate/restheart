# RESTHeart Helm Chart

This document describes the Helm chart for deploying RESTHeart on Kubernetes.

## Overview

The chart is located in `chart/` and deploys RESTHeart as a Deployment with configurable replicas, probes, security contexts, autoscaling, ingress, network policies, and more.

RESTHeart connects to an external MongoDB instance. The chart does not bundle MongoDB.

## Chart Files

```
chart/
├── Chart.yaml                    # Metadata, version, maintainers
├── values.yaml                   # All configurable values
├── RESTHEART-CONFIG.md           # RESTHeart config reference
├── README.md                     # Install/upgrade guide
├── .helmignore
└── templates/
    ├── _helpers.tpl              # Name, label, and service account helpers
    ├── deployment.yaml           # Main Deployment
    ├── service.yaml              # ClusterIP/LoadBalancer/NodePort service
    ├── serviceaccount.yaml       # Optional ServiceAccount
    ├── secret.yaml               # Generated config Secret (when useExternalConfig=false)
    ├── ingress.yaml              # networking.k8s.io/v1 Ingress
    ├── hpa.yaml                  # autoscaling/v2 HPA
    ├── pdb.yaml                  # policy/v1 PodDisruptionBudget
    ├── networkpolicy.yaml        # Optional NetworkPolicy
    ├── NOTES.txt                 # Post-install instructions
    └── tests/
        └── test-connection.yaml  # Helm test (curl /ping)
```

## Install

From GHCR (OCI registry):

```bash
helm install my-restheart oci://ghcr.io/softinstigate/restheart --version 9.5.0
```

Or from the chart directory:

```bash
helm install my-restheart ./chart -f my-values.yaml
```

## Key Configuration

### RESTHeart Application

The `restHeartConfiguration` section in `values.yaml` maps directly to `restheart.yml`. It controls listeners, MongoDB connection, authentication, authorization, logging, and plugin configuration.

See [RESTHEART-CONFIG.md](../chart/RESTHEART-CONFIG.md) for the full reference.

Critical settings to configure:

```yaml
restHeartConfiguration:
  mongo-uri: "mongodb://user:pass@host:27017"
  instance-base-url: "https://api.example.com"
  authenticators:
    mongoRealmAuthenticator:
      create-user-document: '{"_id": "admin", "password": "$2a$12$HASH", "roles": ["admin"]}'
```

### Container and Service

| Value | Default | Description |
|-------|---------|-------------|
| `containerPort` | `8080` | Container port (set to `4443` for HTTPS) |
| `service.type` | `ClusterIP` | Service type |
| `service.port` | `80` | Service port |
| `service.annotations` | `{}` | Cloud provider annotations |

### Probes

All probes are fully configurable. Defaults target RESTHeart's `/ping` endpoint:

```yaml
livenessProbe:
  httpGet:
    path: /ping
    port: http
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

The startup probe allows up to 150 seconds (30 failures x 5s) for the JVM to start.

### Security

Secure defaults are set in `podSecurityContext` and `securityContext`:

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

Override for development if needed by setting these to `{}`.

### Lifecycle

A `preStop` hook sleeps 5 seconds to allow load balancers to drain connections. `terminationGracePeriodSeconds` defaults to 30.

### Autoscaling

```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

Uses `autoscaling/v2` API with the standard `target.averageUtilization` format.

### Ingress

```yaml
ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: api-tls
      hosts:
        - api.example.com
```

Requires Kubernetes 1.22+ (`networking.k8s.io/v1`).

### MongoDB Init Container

Prevents crash loops when MongoDB is in the same cluster:

```yaml
waitForMongo:
  enabled: true
  host: "mongo.mongodb.svc.cluster.local"
  port: 27017
```

### Sidecars and Extra Volumes

```yaml
extraContainers:
  - name: metrics-exporter
    image: prom/jmx-exporter:latest

extraVolumes:
  - name: keystore
    secret:
      secretName: restheart-tls

extraVolumeMounts:
  - name: keystore
    mountPath: /opt/restheart/etc/keystore
    readOnly: true
```

### Network Policy

Restrict ingress to the ingress controller and egress to MongoDB:

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
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: mongodb
      ports:
        - port: 27017
```

### Topology and Priority

```yaml
# Prefer spreading across nodes (alternative to podAntiAffinity)
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        app.kubernetes.io/name: restheart

priorityClassName: high-priority
```

### External Config

Mount config from a pre-managed Secret instead of generating one:

```yaml
useExternalConfig: true
externalConfig:
  name: "restheart-config"
  key: "restheart.yml"
```

When enabled, the chart skips generating the Secret from `restHeartConfiguration`.

## Upgrading

The Deployment includes a `checksum/config` annotation derived from the rendered Secret. Changes to `restHeartConfiguration` trigger a rolling restart automatically.

```bash
helm upgrade my-restheart ./chart -f my-values.yaml
```

## Testing

```bash
helm test my-restheart
```

Runs a Pod that curls `/ping` and verifies the response contains "RESTHeart".

## Publishing

The chart is published as an OCI artifact to GitHub Container Registry on every release tag (`9.x.x`).

```bash
# The CI does this automatically, but to publish manually:
helm package chart --version 9.x.x --app-version 9.x.x
helm push restheart-9.x.x.tgz oci://ghcr.io/softinstigate
```

Requires `GITHUB_TOKEN` with `packages:write` permission.

### Install from GHCR

```bash
helm install my-restheart oci://ghcr.io/softinstigate/restheart --version 9.x.x
```

## Versioning

| Component | Version |
|-----------|---------|
| Chart API | v2 |
| Chart version | 0.2.0 |
| App version | 9.5.0 |
| Minimum Kubernetes | 1.22+ |
| Minimum Helm | 3.x |
