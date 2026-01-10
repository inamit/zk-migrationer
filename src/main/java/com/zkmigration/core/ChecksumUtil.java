package com.zkmigration.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zkmigration.model.ChangeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChecksumUtil {
    private static final Logger logger = LoggerFactory.getLogger(ChecksumUtil.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    public static String calculateChecksum(ChangeSet changeSet) {
        try {
            String changesJson = mapper.writeValueAsString(changeSet.getChanges());
            String rawString = changeSet.getId() + ":" + changeSet.getAuthor() + ":" + changesJson;

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(rawString.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate checksum for ChangeSet " + changeSet.getId(), e);
        }
    }
}
