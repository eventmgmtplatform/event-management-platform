package com.eventmanagement.processor.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed SHA-256: stable replay identity, NOT an occurrence fingerprint. */
public final class StableIdentity {
    private StableIdentity() {}
    public static String of(String purpose, String... fields) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, purpose);
            for (var field : fields) update(digest, field);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void update(MessageDigest digest, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
