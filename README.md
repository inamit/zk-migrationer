# Zookeeper Migration Tool

A Liquibase-inspired tool for managing Zookeeper state migrations. It supports change management via YAML or JSON changelogs, tracks execution history in Zookeeper itself, and supports rollbacks.

## Features

*   **Platform Agnostic**: Works with any Zookeeper ensemble.
*   **Format Support**: Write changelogs in YAML or JSON.
*   **Change Tracking**: Stores executed changesets in Zookeeper to prevent re-execution.
*   **Locking**: Uses Zookeeper distributed locks to prevent concurrent migrations.
*   **Rollback**: Supports rolling back changesets.
*   **Operations**: Create, Update, Delete ZNodes.

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

### Update

Applies pending changesets to the Zookeeper cluster.

```bash
java -jar target/zookeeper-migration-tool-1.0-SNAPSHOT.jar update \
  --connection localhost:2181 \
  --file changelog.yaml
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

### YAML Example

```yaml
databaseChangeLog:
  - changeSet:
      id: "1"
      author: "jules"
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
      changes:
        - update:
            path: "/config/app"
            data: "new-config"
```

### JSON Example

```json
{
  "databaseChangeLog": [
    {
      "changeSet": {
        "id": "1",
        "author": "jules",
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

You can split your changelogs into multiple files.

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
