package com.example.demo.services;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationRequestHasher {

    public String hash(NotificationRequestValidator.ValidatedRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(
                    request.type(),
                    request.recipient(),
                    request.message(),
                    request.payload() == null ? "" : request.payload(),
                    request.scheduledAt() == null ? "" : request.scheduledAt().toString())) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
