package se.eplatform.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical JSON (RFC 8785) and its SHA-256, the way every contract document
 * is hashed. Independent of the application's JSON settings (nulls are kept),
 * so anyone can recompute a hash with any RFC 8785 implementation.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalJson() {}

    public static String canonical(Object document) {
        try {
            return new JsonCanonicalizer(MAPPER.writeValueAsString(document)).getEncodedString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Not canonicalizable as JSON", e);
        }
    }

    public static String hash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hashOf(Object document) {
        return hash(canonical(document));
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
