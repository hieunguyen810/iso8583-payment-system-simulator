# Postgres Helm Chart

## Introduction

A lightweight postgres helm chart for development purposes, check out:
- https://www.postgresql.org/

## Installing the Chart

To install the chart with the release name `postgres` run:

```bash
$ helm repo add postgres https://raw.githubusercontent.com/hansehe/postgres-helm/master/helm/charts/postgres
$ helm install postgres postgres/postgres
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart. For example,

```bash
$ helm install postgres -f values.yaml postgres/postgres
```

## Configuration

Find all possible configuration values here:
- https://github.com/hansehe/postgres-helm/blob/master/helm/postgres/values.yaml
