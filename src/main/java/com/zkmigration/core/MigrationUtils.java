package com.zkmigration.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MigrationUtils {
    public static byte[] resolveData(String data, String file) throws IOException {
        if (data != null && file != null) {
            throw new IllegalArgumentException("Cannot provide both 'data' and 'file'");
        }
        if (file != null) {
            return Files.readAllBytes(Path.of(file));
        }
        return data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }
}
