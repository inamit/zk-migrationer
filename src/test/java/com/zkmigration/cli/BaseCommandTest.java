package com.zkmigration.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseCommandTest {

    // Subclass to access protected methods
    static class TestCommand extends BaseCommand {
        @Override
        public Integer call() {
            return 0;
        }

        // Expose for testing
        public void setCustomVars(Map<String, String> vars) {
            this.customVars = vars;
        }
    }

    @Test
    void testResolveVariables() {
        TestCommand cmd = new TestCommand();

        // Case 1: All null
        Map<String, String> vars = cmd.resolveVariables(null);
        assertTrue(vars.isEmpty());

        // Case 2: Only env
        vars = cmd.resolveVariables("prod");
        assertEquals(1, vars.size());
        assertEquals("prod", vars.get("env"));

        // Case 3: Only custom vars
        Map<String, String> custom = new HashMap<>();
        custom.put("foo", "bar");
        cmd.setCustomVars(custom);
        vars = cmd.resolveVariables(null);
        assertEquals(1, vars.size());
        assertEquals("bar", vars.get("foo"));

        // Case 4: Both
        vars = cmd.resolveVariables("prod");
        assertEquals(2, vars.size());
        assertEquals("prod", vars.get("env"));
        assertEquals("bar", vars.get("foo"));

        // Case 5: Custom overrides env (if collision happens? code allows it if custom is added second)
        // Implementation:
        // variables.put("env", environment);
        // variables.putAll(customVars);
        // So custom vars overwrite 'env' if passed as custom var.
        custom.put("env", "override");
        cmd.setCustomVars(custom);
        vars = cmd.resolveVariables("original");
        assertEquals("override", vars.get("env"));
    }

    @Test
    void testConfirmExecution() throws Exception {
        TestCommand cmd = new TestCommand();
        InputStream originalIn = System.in;
        try {
            // Case 1: No changes -> returns false immediately
            assertFalse(cmd.confirmExecution(false));

            // Case 2: Has changes, user inputs 'y'
            System.setIn(new ByteArrayInputStream("y\n".getBytes()));
            assertTrue(cmd.confirmExecution(true));

            // Case 3: Has changes, user inputs 'Y'
            System.setIn(new ByteArrayInputStream("Y\n".getBytes()));
            assertTrue(cmd.confirmExecution(true));

            // Case 4: Has changes, user inputs 'n'
            System.setIn(new ByteArrayInputStream("n\n".getBytes()));
            assertFalse(cmd.confirmExecution(true));

            // Case 5: Has changes, user inputs 'x'
            System.setIn(new ByteArrayInputStream("x\n".getBytes()));
            assertFalse(cmd.confirmExecution(true));

        } finally {
            System.setIn(originalIn);
        }
    }
}
