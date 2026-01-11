
## Interactive Mode

The CLI supports an interactive mode using the `-i` or `--interactive` flag for both `update` and `rollback` commands.

In this mode, the tool will:
1.  Calculate pending changes (or rollbacks).
2.  Display a preview of the changes, including diffs for node data.
3.  Prompt you to confirm the execution.

**Usage:**

```bash
# Update with preview
java -jar zookeeper-migration-tool.jar update \
  -c localhost:2181 \
  -f changelog.json \
  --context production \
  --labels db \
  --interactive

# Rollback with preview
java -jar zookeeper-migration-tool.jar rollback \
  -c localhost:2181 \
  -f changelog.json \
  --count 1 \
  --interactive
```
