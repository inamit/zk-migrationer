package com.zkmigration.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

public class MigrationUtilsTest {

    @Test
    public void testResolveDataConflict() {
        assertThrows(IllegalArgumentException.class, () -> {
            MigrationUtils.resolveData("data", "file.txt");
        });
    }

    @Test
    public void testResolveDataString() throws IOException {
        byte[] data = MigrationUtils.resolveData("test", null);
        assertThat(new String(data)).isEqualTo("test");
    }

    @Test
    public void testResolveDataNull() throws IOException {
        byte[] data = MigrationUtils.resolveData(null, null);
        assertThat(data).isEmpty();
    }
}
