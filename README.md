# Zookeeper Migration Tool

A Liquibase-inspired tool for managing Zookeeper state migrations. It supports change management via YAML or JSON changelogs, tracks execution history in Zookeeper itself, and supports rollbacks.

## Features

*   **Platform Agnostic**: Works with any Zookeeper ensemble.
*   **Format Support**: Write changelogs in YAML or JSON.
*   **Change Tracking**: Stores executed changesets in Zookeeper to prevent re-execution.
*   **Locking**: Uses Zookeeper distributed locks to prevent concurrent migrations.
*   **Rollback**: Supports rolling back changesets.
*   **Operations**: Create, Update, Delete ZNodes.
*   **Checksum Validation**: Ensures historical changesets have not been modified.
*   **Contexts & Labels**: Control execution scope with environments (e.g., `dev`, `prod`) and labels.

## Installation

### Prerequisites

*   Java 17 or higher
*   Maven 3.6+ (for building)

### Build from Source

```bash
git clone https://github.com/your-repo/zookeeper-migration-tool.git
cd zookeeper-migration-tool
mvn clean package
```

The executable JAR will be located at `target/zookeeper-migration-tool-1.0-SNAPSHOT.jar`.

## Usage

The CLI supports two main commands: `update` and `rollback`.

### Common Arguments

*   `-c, --connection <string>`: Zookeeper connection string (e.g., `localhost:2181`).
*   `-f, --file <file>`: Path to the changelog file (YAML or JSON).
*   `-p, --path <path>`: Root path for migration history (default: `/zookeeper-migrations`).

### Update

Applies pending changesets to the Zookeeper cluster.

**Arguments:**
*   `--context <string>`: (Required) The execution context (e.g., `dev`, `prod`). Changesets matching this context (or "All") will run.
*   `--labels <string>`: (Required) Comma-separated list of labels. Changesets matching at least one label will run.

```bash
java -jar target/zookeeper-migration-tool-1.0-SNAPSHOT.jar update \
  --connection localhost:2181 \
  --file changelog.yaml \
  --context dev \
  --labels app,db
```

### Rollback

Rolls back the last N executed changesets.

```bash
java -jar target/zookeeper-migration-tool-1.0-SNAPSHOT.jar rollback \
  --connection localhost:2181 \
  --file changelog.yaml \
  --count 1
```

## Changelog Format

### Mandatory Fields
*   **context**: Defines the environment(s) for the changeset. Can be a single string or list. Use "All" to run in all contexts.
*   **labels**: logical tags for the changeset. Can be a single string or list.

### Context Groups
You can define Context Groups at the root of the changelog to group environments.

```yaml
contextGroups:
  k8s:
    - dev
    - staging
    - prod
```
If you run with `--context=dev`, changesets marked with `k8s` will also execute because `dev` is part of the `k8s` group.

### YAML Example

```yaml
databaseChangeLog:
  - changeSet:
      id: "1"
      author: "jules"
      context: "dev"
      labels: "init"
      changes:
        - create:
            path: "/config/app"
            data: "default-config"
      rollback:
        - delete:
            path: "/config/app"
  - changeSet:
      id: "2"
      author: "jules"
      context: "prod"
      labels: "init"
      changes:
        - update:
            path: "/config/app"
            data: "new-config"
```

### Checksum Validation
The tool calculates an MD5 checksum for each changeset (ID, author, and changes). If you modify an already-executed changeset, the migration will fail.

To bypass this (e.g., valid refactoring), add the new checksum to `validCheckSum`:

```yaml
  - changeSet:
      id: "1"
      author: "jules"
      context: "dev"
      labels: "init"
      validCheckSum:
        - "7:2dfb1..."
      changes: ...
```

### JSON Example

```json
{
  "databaseChangeLog": [
    {
      "changeSet": {
        "id": "1",
        "author": "jules",
        "context": ["dev", "staging"],
        "labels": ["v1"],
        "changes": [
          {
            "create": {
              "path": "/config/db",
              "data": "db-connection-string"
            }
          }
        ]
      }
    }
  ]
}
```

### Include Nested Files

You can split your changelogs into multiple files. Included files inherit context and labels from the parent.

```yaml
databaseChangeLog:
  - include:
      file: "migrations/v1/changelog.yaml"
  - include:
      file: "migrations/v2/changelog.yaml"
```

## Contributing

1.  Fork the repository.
2.  Create your feature branch (`git checkout -b feature/amazing-feature`).
3.  Commit your changes (`git commit -m 'Add some amazing feature'`).
4.  Push to the branch (`git push origin feature/amazing-feature`).
5.  Open a Pull Request.

## Testing

Run unit and integration tests with Maven:

```bash
mvn test
```
