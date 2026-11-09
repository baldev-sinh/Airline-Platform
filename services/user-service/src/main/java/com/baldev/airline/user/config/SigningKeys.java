package com.baldev.airline.user.config;

import com.baldev.airline.common.security.JwksKeyResolver.Jwk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The RSA key that signs access tokens, plus the public keys published for verification.
 *
 * <p><b>Rotation</b>: generate a new key, set it as {@code JWT_PRIVATE_KEY} (new {@code JWT_KEY_ID}) and move the
 * old public key to {@code airline.security.signing.previous-keys}. Verifiers pick up the new key the first time
 * they see its {@code kid}; tokens signed with the old key keep working until they expire (15 minutes), after
 * which the old key can be dropped.
 */
@Slf4j
@Component
public class SigningKeys {

    private static final int MIN_KEY_BITS = 2048;

    private final PrivateKey privateKey;
    private final String currentKeyId;
    private final List<Jwk> published;
    private final Map<String, RSAPublicKey> verificationKeys;

    public SigningKeys(SecurityProperties properties) {
        SecurityProperties.Signing signing = properties.security().signing();
        RSAPublicKey publicKey;
        try {
            if (signing.privateKey() != null && !signing.privateKey().isBlank()) {
                this.privateKey = parsePrivateKey(signing.privateKey());
                publicKey = publicKeyOf((RSAPrivateCrtKey) privateKey);
            } else if (signing.generateEphemeralKey()) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(MIN_KEY_BITS);
                KeyPair pair = generator.generateKeyPair();
                this.privateKey = pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
                log.warn("*** Using an EPHEMERAL token signing key: every restart logs everyone out. "
                        + "Set JWT_PRIVATE_KEY outside local development. ***");
            } else {
                throw new IllegalStateException("airline.security.signing.private-key (JWT_PRIVATE_KEY) is required");
            }
        } catch (GeneralSecurityException | ClassCastException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid token signing key (expected an RSA PKCS#8 PEM)", e);
        }
        if (publicKey.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("The token signing key must be at least " + MIN_KEY_BITS + " bits");
        }
        this.currentKeyId = signing.keyId() != null && !signing.keyId().isBlank() ? signing.keyId() : thumbprint(publicKey);

        Map<String, RSAPublicKey> verification = new LinkedHashMap<>();
        verification.put(currentKeyId, publicKey);
        for (SecurityProperties.PublishedKey previous : signing.previousKeys()) {
            try {
                verification.put(previous.keyId(), parsePublicKey(previous.publicKey()));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException("Invalid previous public key " + previous.keyId(), e);
            }
        }
        this.verificationKeys = Collections.unmodifiableMap(verification);
        this.published = verification.entrySet().stream().map(e -> toJwk(e.getKey(), e.getValue())).toList();
        log.info("Token signing key {} active; publishing {} key(s)", currentKeyId, published.size());
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public String currentKeyId() {
        return currentKeyId;
    }

    /** The same public keys by key id, for verifying tokens in this service without a network round trip. */
    public Map<String, RSAPublicKey> verificationKeys() {
        return verificationKeys;
    }

    /** Public keys for the JWKS document: the current key first, then retired keys still in use. */
    public List<Jwk> publishedKeys() {
        return published;
    }

    private static Jwk toJwk(String kid, RSAPublicKey key) {
        return new Jwk("RSA", kid, "sig", "RS256", base64Url(key.getModulus()), base64Url(key.getPublicExponent()));
    }

    /** RFC 7638 JWK thumbprint: SHA-256 over the canonical {e, kty, n} JSON. */
    static String thumbprint(RSAPublicKey key) {
        String canonical = "{\"e\":\"" + base64Url(key.getPublicExponent()) + "\",\"kty\":\"RSA\",\"n\":\""
                + base64Url(key.getModulus()) + "\"}";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) { // drop the sign byte
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der(pem, "PRIVATE KEY")));
    }

    private static RSAPublicKey parsePublicKey(String pem) throws GeneralSecurityException {
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(pem, "PUBLIC KEY")));
    }

    private static RSAPublicKey publicKeyOf(RSAPrivateCrtKey key) throws GeneralSecurityException {
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
    }

    private static byte[] der(String pem, String type) {
        String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        if (body.contains("-----")) {
            throw new IllegalArgumentException("Expected a " + type + " PEM block (PKCS#8 / X.509)");
        }
        return Base64.getDecoder().decode(body);
    }
}
