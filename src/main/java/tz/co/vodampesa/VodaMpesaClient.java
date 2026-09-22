package tz.co.vodampesa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tz.co.vodampesa.exception.VodaMpesaException;
import tz.co.vodampesa.model.VodaMpesaResponse;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Christopher oigo
 * HTTP and session layer. Sessions are cached per client; transaction requests are never retried.
 */
public final class VodaMpesaClient {
    private final VodaMpesaConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final Clock clock;
    private final PublicKey publicKey;
    private Session session;

    public VodaMpesaClient(VodaMpesaConfig config) {
        this(config, Clock.systemUTC());
    }

    VodaMpesaClient(VodaMpesaConfig config, Clock clock) {
        this.config = new VodaMpesaConfig(config);
        this.config.validate();
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(config.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            String encoded = config.getPublicKey().replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            this.publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("publicKey must be an RSA X.509 PUBLIC KEY in PEM or Base64 format");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static VodaMpesaException failure(String message, String code, Integer status) {
        return new VodaMpesaException(message, code, status, null);
    }

    public synchronized boolean isInitialized() {
        return session != null && clock.instant().isBefore(session.expiresAt());
    }

    /**
     * Discards the cached session; next call authenticates again. Does not send a payment.
     */
    public synchronized void invalidateSession() {
        session = null;
    }

    /**
     * Obtains a session if none is cached or its configured lifetime has elapsed.
     */
    public void initialize() {
        sessionKey();
    }

    private synchronized String sessionKey() {
        if (isInitialized()) return session.token();
        Instant requestedAt = clock.instant();
        JsonNode response = exchange(config.getSessionPath(), "GET", null, encryptedApiKey());
        String code = response.path("output_ResponseCode").asText(null);
        if (!"INS-0".equals(code)) throw failure("M-Pesa authentication was rejected", code, null);
        String key = response.path("output_SessionID").asText(null);
        if (key == null || key.isBlank()) key = response.path("output_SessionKey").asText(null);
        if (key == null || key.isBlank() || key.contains("\r") || key.contains("\n"))
            throw failure("M-Pesa authentication response has no valid session key", code, null);
        session = new Session(key, requestedAt.plus(config.getSessionLifetime()));
        return key;
    }

    public VodaMpesaResponse send(String path, String method, Map<String, String> payload) {
        String token = sessionKey();
        JsonNode response;
        try {
            response = exchange(path, method, payload, token);
        } catch (VodaMpesaException e) {
            if (Integer.valueOf(401).equals(e.getHttpStatus()) || Integer.valueOf(403).equals(e.getHttpStatus())
                    || "INS-26".equals(e.getResponseCode())) invalidateIfCurrent(token);
            throw e;
        }
        if (!response.path("output_ResponseCode").isTextual() || response.path("output_ResponseCode").asText().isBlank())
            throw failure("M-Pesa response is missing output_ResponseCode", null, null);
        if ("INS-26".equals(response.path("output_ResponseCode").asText())) invalidateIfCurrent(token);
        try {
            return mapper.treeToValue(response, VodaMpesaResponse.class);
        } catch (IOException e) {
            throw failure("Invalid M-Pesa response fields", null, null);
        }
    }

    private synchronized void invalidateIfCurrent(String token) {
        if (session != null && session.token().equals(token)) session = null;
    }

    private String encryptedApiKey() {
        try {
            Cipher cipher;
            if (config.getRsaPadding() == VodaMpesaConfig.RsaPadding.PKCS1) {
                cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            } else {
                boolean sha256 = config.getRsaPadding() == VodaMpesaConfig.RsaPadding.OAEP_SHA256;
                cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
                cipher.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                        sha256 ? "SHA-256" : "SHA-1", "MGF1",
                        sha256 ? MGF1ParameterSpec.SHA256 : MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
            }
            return Base64.getEncoder().encodeToString(cipher.doFinal(config.getApiKey().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw failure("Cannot encrypt API key; check the public key and RSA padding configuration", null, null);
        }
    }

    private JsonNode exchange(String path, String method, Map<String, String> payload, String token) {
        try {
            URI uri = URI.create(config.effectiveBaseUrl()).resolve(path);
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();
            if (payload != null) {
                if ("GET".equals(method)) {
                    String query = payload.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                            .collect(Collectors.joining("&"));
                    uri = URI.create(uri + "?" + query);
                } else {
                    body = HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload));
                }
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(config.getRequestTimeout())
                    .header("Authorization", "Bearer " + token).header("Origin", config.getOrigin())
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .method(method, body).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            JsonNode json;
            try {
                json = mapper.readTree(response.body());
            } catch (IOException e) {
                throw failure(ok ? "M-Pesa returned invalid JSON" : "M-Pesa HTTP request failed", null, response.statusCode());
            }
            if (!ok) throw failure("M-Pesa HTTP request failed",
                    json == null ? null : json.path("output_ResponseCode").asText(null), response.statusCode());
            if (json == null || !json.isObject())
                throw failure("M-Pesa returned an empty or invalid response", null, response.statusCode());
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("M-Pesa request interrupted; reconcile transaction status before resubmitting", null, null);
        } catch (IOException e) {
            throw failure("M-Pesa transport failure; transaction outcome may be unknown. Query status before resubmitting", null, null);
        }
    }

    private record Session(String token, Instant expiresAt) {
        @Override
        public String toString() {
            return "Session[redacted]";
        }
    }
}
