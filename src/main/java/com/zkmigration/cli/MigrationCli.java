package com.zkmigration.cli;

import com.zkmigration.core.MigrationService;
import com.zkmigration.model.ChangeLog;
import com.zkmigration.parser.ChangeLogParser;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "zkmigration", mixinStandardHelpOptions = true, version = "1.0",
        description = "Zookeeper Migration Tool", subcommands = {UpdateCommand.class, RollbackCommand.class})
public class MigrationCli implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MigrationCli()).execute(args);
        System.exit(exitCode);
    }
}

abstract class BaseCommand implements Callable<Integer> {
    @Option(names = {"-c", "--connection"}, description = "Zookeeper connection string", required = true)
    protected String connectionString;

    @Option(names = {"-f", "--file"}, description = "Path to changelog file", required = true)
    protected File changeLogFile;

    @Option(names = {"-p", "--path"}, description = "Root path for migration history", defaultValue = "/zookeeper-migrations")
    protected String historyPath;

    protected CuratorFramework createClient() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionString, new ExponentialBackoffRetry(1000, 3));
        client.start();
        return client;
    }
}

@Command(name = "update", description = "Apply pending migrations")
class UpdateCommand extends BaseCommand {
    private static final Logger logger = LoggerFactory.getLogger(UpdateCommand.class);

    @Option(names = {"--context"}, description = "Execution context", required = true)
    private String context;

    @Option(names = {"--labels"}, description = "Execution labels (comma separated)", required = true)
    private String labels;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting update...");
        try (CuratorFramework client = createClient()) {
            ChangeLogParser parser = new ChangeLogParser();
            ChangeLog changeLog = parser.parse(changeLogFile);

            List<String> labelList = Arrays.asList(labels.split(","));

            MigrationService service = new MigrationService(client, historyPath);
            service.update(changeLog, context, labelList);
            logger.info("Update complete.");
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }
}

@Command(name = "rollback", description = "Rollback the last N executed migrations")
class RollbackCommand extends BaseCommand {
    private static final Logger logger = LoggerFactory.getLogger(UpdateCommand.class);

    @Option(names = {"-n", "--count"}, description = "Number of changesets to rollback", defaultValue = "1")
    private int count;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting rollback...");
        try (CuratorFramework client = createClient()) {
            ChangeLogParser parser = new ChangeLogParser();
            ChangeLog changeLog = parser.parse(changeLogFile);

            MigrationService service = new MigrationService(client, historyPath);
            service.rollback(changeLog, count);
            logger.info("Rollback complete.");
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }
}
