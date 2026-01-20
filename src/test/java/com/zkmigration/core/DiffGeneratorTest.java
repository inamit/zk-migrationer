package com.zkmigration.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class DiffGeneratorTest {

    @Test
    public void testIdentical() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        String diff = DiffGenerator.generateDiff(data, data);
        assertThat(diff).isEmpty();
    }

    @Test
    public void testSimpleChange() {
        byte[] oldData = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] newData = "world".getBytes(StandardCharsets.UTF_8);
        String diff = DiffGenerator.generateDiff(oldData, newData);
        // "hello" -> "world" is a modified line (one word change in this naive impl)
        assertThat(diff).contains("* [-hello-] {+world+}");
    }

    @Test
    public void testMultilineChange() {
        String oldStr = "line1\nline2\nline3";
        String newStr = "line1\nline2-modified\nline3";
        String diff = DiffGenerator.generateDiff(oldStr.getBytes(StandardCharsets.UTF_8), newStr.getBytes(StandardCharsets.UTF_8));

        assertThat(diff).doesNotContain("- line1"); // environment ignored in my naive impl
        // With word diff update, it should show as modified line with word diffs
        assertThat(diff).contains("* [-line2-] {+line2-modified+}");
    }

    @Test
    public void testWordDiff() {
        String oldStr = "this is a test";
        String newStr = "this is a new test";
        String diff = DiffGenerator.generateDiff(oldStr.getBytes(StandardCharsets.UTF_8), newStr.getBytes(StandardCharsets.UTF_8));
        assertThat(diff).contains("* [-this is a test-] {+this is a new test+}");
    }

    @Test
    public void testBinary() {
        byte[] oldData = new byte[]{1, 2, 3, 0, 4};
        byte[] newData = new byte[]{1, 2, 3, 0, 5};
        String diff = DiffGenerator.generateDiff(oldData, newData);
        assertThat(diff).contains("Binary data differs");
    }

    @Test
    public void testNullHandling() {
        byte[] newData = "new".getBytes(StandardCharsets.UTF_8);
        String diff = DiffGenerator.generateDiff(null, newData);
        assertThat(diff).contains("+ new");
    }
}
