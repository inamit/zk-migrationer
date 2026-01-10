package com.zkmigration.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationCliTest {

    @Test
    void testMainCommandHelp() {
        int exitCode = new CommandLine(new MigrationCli()).execute("--help");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void testMainCommandNoArgs() {
        // Calling call() directly on the main CLI command
        MigrationCli cli = new MigrationCli();
        try {
            int exitCode = cli.call();
            assertThat(exitCode).isEqualTo(0);
        } catch (Exception e) {
            // Should not happen
        }
    }
}
