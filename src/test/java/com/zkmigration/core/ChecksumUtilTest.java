package com.zkmigration.core;

import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Change;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumUtilTest {

    @Test
    void testCalculateChecksumConsistency() {
        ChangeSet cs1 = new ChangeSet();
        cs1.setId("1");
        cs1.setAuthor("me");
        Create c1 = new Create();
        c1.setPath("/path");
        c1.setData("data");
        cs1.setChanges(List.of(c1));

        String sum1 = ChecksumUtil.calculateChecksum(cs1);

        ChangeSet cs2 = new ChangeSet();
        cs2.setId("1");
        cs2.setAuthor("me");
        Create c2 = new Create();
        c2.setPath("/path");
        c2.setData("data");
        cs2.setChanges(List.of(c2));

        String sum2 = ChecksumUtil.calculateChecksum(cs2);

        assertThat(sum1).isEqualTo(sum2);
    }

    @Test
    void testCalculateChecksumChanges() {
        ChangeSet cs1 = new ChangeSet();
        cs1.setId("1");
        cs1.setAuthor("me");
        Create c1 = new Create();
        c1.setPath("/path");
        c1.setData("data");
        cs1.setChanges(List.of(c1));

        String sum1 = ChecksumUtil.calculateChecksum(cs1);

        // Change content
        c1.setData("newData");
        String sum2 = ChecksumUtil.calculateChecksum(cs1);

        assertThat(sum1).isNotEqualTo(sum2);
    }
}
