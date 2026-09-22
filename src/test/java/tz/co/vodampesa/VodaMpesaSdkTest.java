package tz.co.vodampesa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tz.co.vodampesa.exception.VodaMpesaException;
import tz.co.vodampesa.model.C2bPaymentRequest;
import tz.co.vodampesa.model.QueryStatusRequest;
import tz.co.vodampesa.model.ReversalRequest;
import tz.co.vodampesa.model.VodaMpesaResult;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VodaMpesaSdkTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String ACCEPTED = "{\"output_ResponseCode\":\"INS-0\",\"output_TransactionID\":\"TX-1\",\"output_ConversationID\":\"C-1\",\"output_ThirdPartyConversationID\":\"request-id\",\"futureField\":true}";
    static KeyPair keys;
    HttpServer server;
    VodaMpesaConfig config;
    List<Call> calls;
    Queue<Reply> replies;
    AtomicInteger sessionCalls;
    String sessionBody;

    @BeforeAll
    static void keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @BeforeEach
    void setup() throws Exception {
        calls = new CopyOnWriteArrayList<>();
        replies = new ConcurrentLinkedQueue<>();
        sessionCalls = new AtomicInteger();
        sessionBody = "{\"output_ResponseCode\":\"INS-0\",\"output_SessionID\":\"session-secret\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Call call = new Call(exchange.getRequestURI().getPath(), exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Origin"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), exchange.getRequestURI().getRawQuery());
            calls.add(call);
            Reply reply;
            if (call.path().endsWith("getSession/")) {
                sessionCalls.incrementAndGet();
                reply = new Reply(200, sessionBody);
            } else {
                reply = replies.poll();
                if (reply == null) reply = new Reply(200, ACCEPTED);
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        config = new VodaMpesaConfig();
        config.setApiKey("api-secret");
        config.setPublicKey("-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        config.setOrigin("merchant.example");
        config.setServiceProviderCode("000000");
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/sandbox/ipg/v2/vodacomTZN/");
    }

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
    }

    Call last() {
        return calls.get(calls.size() - 1);
    }

    JsonNode body() throws Exception {
        return JSON.readTree(last().body());
    }

    @ParameterizedTest
    @EnumSource(VodaMpesaConfig.RsaPadding.class)
    void encryptsApiKeyAndReusesSession(VodaMpesaConfig.RsaPadding padding) throws Exception {
        config.setRsaPadding(padding);
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        assertFalse(sdk.isInitialized());
        assertTrue(sdk.initialize());
        assertTrue(sdk.initialize());
        assertEquals(1, sessionCalls.get());
        assertTrue(sdk.isInitialized());
        Call auth = calls.get(0);
        assertEquals("GET", auth.method());
        assertEquals("", auth.body());
        assertEquals("merchant.example", auth.origin());
        assertEquals("application/json", auth.contentType());
        Cipher decrypt;
        if (padding == VodaMpesaConfig.RsaPadding.PKCS1) {
            decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            decrypt.init(Cipher.DECRYPT_MODE, keys.getPrivate());
        } else {
            boolean sha256 = padding == VodaMpesaConfig.RsaPadding.OAEP_SHA256;
            decrypt = Cipher.getInstance("RSA/ECB/OAEPPadding");
            decrypt.init(Cipher.DECRYPT_MODE, keys.getPrivate(), new OAEPParameterSpec(sha256 ? "SHA-256" : "SHA-1", "MGF1",
                    sha256 ? MGF1ParameterSpec.SHA256 : MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
        }
        assertEquals("api-secret", new String(decrypt.doFinal(Base64.getDecoder().decode(auth.authorization().substring(7))), StandardCharsets.UTF_8));
        sdk.collectPayment("000000000001", 1500, "ORDER-1", "Order payment");
        assertEquals("Bearer session-secret", last().authorization());
        assertEquals(1, sessionCalls.get());
    }

    @Test
    void c2bUsesExactPdfFieldsAndDistinguishesAcceptance() throws Exception {
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        VodaMpesaResult result = sdk.c2bPayment(C2bPaymentRequest.builder().amount("1500").customerMsisdn("255712345678")
                .transactionReference("ORD-98765").thirdPartyConversationId("request-id").purchasedItemsDesc("Payment").build());
        assertEquals("/sandbox/ipg/v2/vodacomTZN/c2bPayment/singleStage/", last().path());
        assertEquals("POST", last().method());
        assertEquals(JSON.readTree("""
                {"input_Amount":"1500","input_CustomerMSISDN":"255712345678","input_Country":"TZN","input_Currency":"TZS",
                "input_ServiceProviderCode":"000000","input_TransactionReference":"ORD-98765",
                "input_ThirdPartyConversationID":"request-id","input_PurchasedItemsDesc":"Payment"}
                """), body());
        assertTrue(result.isAccepted());
        assertTrue(result.isSuccess());
        assertFalse(result.isCompleted());
        assertEquals("TX-1", result.getTransactionId().orElseThrow());
        assertEquals("request-id", result.getRequestConversationId());
        assertSame(result, result.throwIfFailed());


    }

    @Test
    void b2cAndB2bUseTheirDistinctWireFields() throws Exception {
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        sdk.sendToCustomer("255712345678", 5000, "SAL-1", "Salary");
        assertTrue(last().path().endsWith("/b2cPayment/"));
        assertEquals(8, body().size());
        assertEquals("Salary", body().path("input_PaymentItemsDesc").asText());
        assertFalse(body().has("input_PurchasedItemsDesc"));
        assertEquals("000000", body().path("input_ServiceProviderCode").asText());
        sdk.sendToBusiness("000001", 10000, "SETTLE-1", "Supplier");
        assertTrue(last().path().endsWith("/b2bPayment/"));
        assertEquals(8, body().size());
        assertEquals("000000", body().path("input_PrimaryPartyCode").asText());
        assertEquals("000001", body().path("input_ReceiverPartyCode").asText());
        assertEquals("Supplier", body().path("input_PurchasedItemsDesc").asText());
        assertFalse(body().has("input_ServiceProviderCode"));
    }

    @Test
    void reversalSupportsAmountAndPortalCredentials() throws Exception {
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        sdk.reverseTransaction("TX-1", 500);
        assertTrue(last().path().endsWith("/reversal"));
        assertEquals(5, body().size());
        assertEquals("500", body().path("input_ReversalAmount").asText());
        assertFalse(body().has("input_Currency"));
        ReversalRequest request = ReversalRequest.builder().transactionId("TX-1").thirdPartyConversationId("rev-1")
                .securityCredential("credential-secret").initiatorIdentifier("initiator").build();
        sdk.reverseTransaction(request);
        assertFalse(body().has("input_ReversalAmount"));
        assertEquals("credential-secret", body().path("input_SecurityCredential").asText());
        assertFalse(request.toString().contains("credential-secret"));
    }

    @Test
    void statusQueryPostAndCompletedResult() throws Exception {
        replies.add(new Reply(200, "{\"output_ResponseCode\":\"INS-0\",\"output_ResponseTransactionStatus\":\"Completed\"}"));
        VodaMpesaResult result = new VodaMpesaSdk(config).queryTransactionStatus("TX-1");
        assertTrue(last().path().endsWith("/queryTransactionStatus/"));
        assertEquals("POST", last().method());
        assertEquals(4, body().size());
        assertEquals("TX-1", body().path("input_QueryReference").asText());
        assertTrue(result.isCompleted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INS-9", "INS-1", "INS-0", "0", "unknown-code"})
    void verifiesPaymentUsingFreshStatusInsteadOfEarlierCode(String earlierCode) throws Exception {
        replies.add(new Reply(200, """
                {"output_ResponseCode":"INS-0", "output_TransactionID":"TX-1",
                 "output_ResponseTransactionStatus":"Completed"}
                """));
        var verification = new VodaMpesaSdk(config).verifyPayment(earlierCode, "TX-1");
        assertTrue(verification.isVerified());
        assertTrue(verification.isTransactionMatched());
        assertEquals(earlierCode, verification.originalResponseCode());
        assertEquals("TX-1", verification.transactionId());
        assertEquals("INS-0", verification.statusResult().getResponseCode());
        assertEquals("TX-1", body().path("input_QueryReference").asText());
        assertEquals("000000", body().path("input_ServiceProviderCode").asText());
        assertEquals("TZN", body().path("input_Country").asText());
        assertDoesNotThrow(() -> UUID.fromString(body().path("input_ThirdPartyConversationID").asText()));
        assertEquals(4, body().size());
        assertEquals(2, calls.size());
        assertTrue(last().path().endsWith("/queryTransactionStatus/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pending", "Failed", "Cancelled", "Expired", "N/A", "FutureStatus", ""})
    void doesNotVerifyUncompletedPayment(String status) {
        replies.add(new Reply(200, "{\"output_ResponseCode\":\"INS-0\",\"output_TransactionID\":\"TX-1\","
                + "\"output_ResponseTransactionStatus\":\"" + status + "\"}"));
        var verification = new VodaMpesaSdk(config).verifyPayment("INS-9", "TX-1");
        assertFalse(verification.isVerified());
        assertEquals(status, verification.statusResult().getTransactionStatus().orElseThrow());
        assertEquals(2, calls.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TX-OTHER", ""})
    void completedStatusMustMatchRequestedTransaction(String returnedId) {
        replies.add(new Reply(200, "{\"output_ResponseCode\":\"INS-0\",\"output_TransactionID\":\"" + returnedId
                + "\",\"output_ResponseTransactionStatus\":\"Completed\"}"));
        var verification = new VodaMpesaSdk(config).verifyPayment("INS-9", "TX-1");
        assertFalse(verification.isVerified());
        assertFalse(verification.isTransactionMatched());
    }

    @Test
    void missingTransactionIdOrRejectedQueryDoesNotVerifyPayment() {
        replies.add(new Reply(200, "{\"output_ResponseCode\":\"INS-0\",\"output_ResponseTransactionStatus\":\"Completed\"}"));
        replies.add(new Reply(200, """
                {"output_ResponseCode":"INS-26", "output_TransactionID":"TX-1",
                 "output_ResponseTransactionStatus":"Completed"}
                """));
        var sdk = new VodaMpesaSdk(config);
        assertFalse(sdk.verifyPayment("INS-9", "TX-1").isVerified());
        var rejected = sdk.verifyPayment("INS-9", "TX-1");
        assertFalse(rejected.isVerified());
        assertEquals("INS-26", rejected.statusResult().getResponseCode());
    }

    @Test
    void verificationValidatesInputsBeforeNetworkAndPropagatesQueryErrors() {
        var sdk = new VodaMpesaSdk(config);
        assertThrows(IllegalArgumentException.class, () -> sdk.verifyPayment(null, "TX-1"));
        assertThrows(IllegalArgumentException.class, () -> sdk.verifyPayment(" ", "TX-1"));
        assertThrows(IllegalArgumentException.class, () -> sdk.verifyPayment("INS-9", null));
        assertThrows(IllegalArgumentException.class, () -> sdk.verifyPayment("INS-9", " "));
        assertTrue(calls.isEmpty());
        replies.add(new Reply(500, "{}"));
        assertThrows(VodaMpesaException.class, () -> sdk.verifyPayment("INS-9", "TX-1"));
        assertEquals(2, calls.size());
    }

    @Test
    void verificationHonorsConfiguredGetQuery() {
        config.setQueryMethod(VodaMpesaConfig.QueryMethod.GET);
        new VodaMpesaSdk(config).verifyPayment("INS-9", "TX-1");
        assertEquals("GET", last().method());
        assertTrue(last().query().contains("input_QueryReference=TX-1"));
        assertEquals("", last().body());
    }

    @Test
    void configurableGetQueryEncodesParametersWithoutBody() {
        config.setQueryMethod(VodaMpesaConfig.QueryMethod.GET);
        new VodaMpesaSdk(config).queryTransactionStatus(new QueryStatusRequest("TX /&?+", "query-id"));
        assertEquals("GET", last().method());
        assertEquals("", last().body());
        assertTrue(last().query().contains("input_QueryReference=TX+%2F%26%3F%2B"));
    }

    @Test
    void b2cPathOverrideAndConfigurationSnapshot() {
        config.setB2cPath("b2cPayment/singleStage/");
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        config.setBaseUrl("https://example.invalid/");
        config.setApiKey("changed");
        sdk.sendToCustomer("255712345678", 1, "REF", "Description");
        assertTrue(last().path().endsWith("/b2cPayment/singleStage/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "NaN", "", "1e3", " 2", "000"})
    void invalidAmountsNeverAuthenticateOrSend(String amount) {
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        assertThrows(IllegalArgumentException.class, () -> sdk.c2bPayment(new C2bPaymentRequest(amount, "255712345678", "REF", "id", "Payment")));
        assertTrue(calls.isEmpty());
    }

    @Test
    void validatesReferencePhoneAndRequiredIdsBeforeNetwork() {
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        assertThrows(IllegalArgumentException.class, () -> sdk.collectPayment("0712345678", 1, "REF", "Payment"));
        assertThrows(IllegalArgumentException.class, () -> sdk.collectPayment("255712345678", 1, "x".repeat(21), "Payment"));
        assertThrows(IllegalArgumentException.class, () -> sdk.queryTransactionStatus(new QueryStatusRequest("TX-1", "")));
        assertThrows(IllegalArgumentException.class, () -> sdk.reverseTransaction("TX-1", 0));
        assertTrue(calls.isEmpty());
    }

    @Test
    void apiFailureIsReturnedAndCanBeThrown() {
        replies.add(new Reply(200, "{\"output_ResponseCode\":\"INS-5\",\"output_ResponseDesc\":\"Cancelled\"}"));
        VodaMpesaResult result = new VodaMpesaSdk(config).collectPayment("255712345678", 1, "REF", "Payment");
        assertFalse(result.isAccepted());
        assertEquals("INS-5", assertThrows(VodaMpesaException.class, result::throwIfFailed).getResponseCode());
        assertEquals(2, calls.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 302})
    void httpErrorsNeverReplayPayment(int status) {
        replies.add(new Reply(status, "{\"output_ResponseCode\":\"INS-26\"}"));
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        VodaMpesaException error = assertThrows(VodaMpesaException.class, () -> sdk.collectPayment("255712345678", 1, "REF", "Payment"));
        assertEquals(status, error.getHttpStatus());
        assertEquals("INS-26", error.getResponseCode());
        assertFalse(sdk.isInitialized());
        assertEquals(2, calls.size());
    }

    @Test
    void providerAuthFailureInvalidatesSessionForNextExplicitCall() {
        replies.add(new Reply(200, "{\"output_ResponseCode\":\"INS-26\"}"));
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        assertFalse(sdk.collectPayment("255712345678", 1, "REF", "Payment").isAccepted());
        assertFalse(sdk.isInitialized());
        assertEquals(2, calls.size());
        sdk.queryTransactionStatus("REF");
        assertEquals(2, sessionCalls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "[]", "not-json", "{\"output_ResponseCode\":null}"})
    void malformedResponsesAreProtocolErrors(String response) {
        replies.add(new Reply(200, response));
        assertThrows(VodaMpesaException.class, () -> new VodaMpesaSdk(config).queryTransactionStatus("TX-1"));
        assertEquals(2, calls.size());
    }

    @Test
    void missingSessionIsNotCachedOrUsed() {
        sessionBody = "{\"output_ResponseCode\":\"INS-0\"}";
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        assertThrows(VodaMpesaException.class, () -> sdk.queryTransactionStatus("TX-1"));
        assertFalse(sdk.isInitialized());
        assertEquals(1, calls.size());
    }

    @Test
    void acceptsSessionKeyAliasAndExplicitInvalidation() {
        sessionBody = "{\"output_ResponseCode\":\"INS-0\",\"output_SessionKey\":\"session-secret\"}";
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        sdk.initialize();
        sdk.invalidateSession();
        assertFalse(sdk.isInitialized());
        sdk.initialize();
        assertEquals(2, sessionCalls.get());
    }

    @Test
    void sessionExpiresUsingConfiguredLifetime() {
        MutableClock clock = new MutableClock();
        config.setSessionLifetime(Duration.ofMinutes(5));
        VodaMpesaClient client = new VodaMpesaClient(config, clock);
        client.initialize();
        clock.now = clock.now.plusSeconds(301);
        assertFalse(client.isInitialized());
        client.initialize();
        assertEquals(2, sessionCalls.get());
    }

    @Test
    void concurrentCallsShareOneAuthentication() throws Exception {
        VodaMpesaSdk sdk = new VodaMpesaSdk(config);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<VodaMpesaResult>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) tasks.add(() -> sdk.queryTransactionStatus("TX-1"));
            for (Future<VodaMpesaResult> future : executor.invokeAll(tasks)) assertTrue(future.get().isAccepted());
            assertEquals(1, sessionCalls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void validatesConfigurationAndRedactsCredentials() {
        assertFalse(config.toString().contains("api-secret"));
        config.setB2cPath("https://elsewhere.example/");
        assertThrows(IllegalArgumentException.class, () -> new VodaMpesaSdk(config));
        config.setB2cPath("../escape");
        assertThrows(IllegalArgumentException.class, () -> new VodaMpesaSdk(config));
        config.setB2cPath("b2cPayment/");
        config.setRequestTimeout(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new VodaMpesaSdk(config));
        config.setRequestTimeout(Duration.ofSeconds(10));
        config.setProduction(true);
        assertThrows(IllegalArgumentException.class, () -> new VodaMpesaSdk(config));
    }

    record Call(String path, String method, String authorization, String origin, String contentType, String body,
                String query) {
    }

    record Reply(int status, String body) {
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }
}
