package tz.co.vodampesa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tz.co.vodampesa.model.VodaMpesaCallback;

import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VodaMpesaCallbackTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PAYLOAD = """
            {
              "input_OriginalConversationID": "1e029550d09745e7b2221bb4b2dc8ffc",
              "input_ThirdPartyReference": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "input_TransactionID": "RvvsqB0rcP3Y",
              "input_ResultCode": "0",
              "input_ResultDesc": "Request Processed Successfully"
            }
            """;
    private static VodaMpesaConfig config;

    @BeforeAll
    static void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        config = new VodaMpesaConfig();
        config.setApiKey("unused-api-key");
        config.setPublicKey(Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded()));
        config.setOrigin("merchant.example");
        config.setServiceProviderCode("000000");
        config.setBaseUrl("https://example.invalid/");
    }

    @Test
    void handlesPdfPayloadAndSerializesExactAcknowledgementWithoutAuthentication() throws Exception {
        var sdk = new VodaMpesaSdk(config);
        var received = new ArrayList<VodaMpesaCallback>();
        var acknowledgement = sdk.handleCallback(PAYLOAD, received::add);
        assertEquals(1, received.size());
        var callback = received.get(0);
        assertTrue(callback.isSuccess());
        assertFalse(callback.isFailed());
        assertEquals("RvvsqB0rcP3Y", callback.transactionId());
        assertEquals("Request Processed Successfully", callback.resultDesc());
        assertEquals(JSON.readTree("""
                {
                  "output_OriginalConversationID": "1e029550d09745e7b2221bb4b2dc8ffc",
                  "output_ResponseCode": "0",
                  "output_ResponseDesc": "Successfully Accepted Result",
                  "output_ThirdPartyConversationID": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                }
                """), JSON.valueToTree(acknowledgement));
        assertEquals(JSON.readTree(PAYLOAD), JSON.valueToTree(callback));
        assertFalse(sdk.isInitialized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "5", "9", "INS-0", "future-code"})
    void failedTransactionsAreHandedOffAndAcknowledged(String code) {
        var service = new VodaMpesaService(config);
        var acknowledgement = service.handleCallback(PAYLOAD.replace("\"0\"", "\"" + code + "\""), callback -> {
            assertFalse(callback.isSuccess());
            assertTrue(callback.isFailed());
            assertEquals(code, callback.resultCode());
        });
        assertEquals("0", acknowledgement.responseCode());
    }

    @Test
    void exposesParserAndIgnoresFutureFields() {
        var sdk = new VodaMpesaSdk(config);
        assertTrue(sdk.parseCallback(PAYLOAD.replace("{", "{\"future\":{\"field\":true},")).isSuccess());
        assertFalse(sdk.isInitialized());
    }

    @Test
    void handlerFailurePropagatesWithoutAcknowledgement() {
        var failure = new IllegalStateException("Database unavailable");
        var sdk = new VodaMpesaSdk(config);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> sdk.handleCallback(PAYLOAD, callback -> { throw failure; })));
    }

    @Test
    void duplicateDeliveriesReachApplicationForDurableDeduplication() {
        var sdk = new VodaMpesaSdk(config);
        var received = new ArrayList<VodaMpesaCallback>();
        var first = sdk.handleCallback(PAYLOAD, received::add);
        var second = sdk.handleCallback(PAYLOAD, received::add);
        assertEquals(first, second);
        assertEquals(2, received.size());
        assertEquals(received.get(0), received.get(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "[]", "true", "{}", "not JSON", "{} {}"})
    void rejectsMalformedOrMissingPayloadBeforeHandler(String body) {
        var calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class,
                () -> new VodaMpesaSdk(config).handleCallback(body, callback -> calls.incrementAndGet()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"input_OriginalConversationID", "input_ThirdPartyReference", "input_ResultCode", "input_TransactionID"})
    void requiresCorrelationCodeAndSuccessfulTransactionId(String field) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(PAYLOAD);
        body.remove(field);
        var sdk = new VodaMpesaSdk(config);
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(body.toString()));
        body.put(field, " ");
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(body.toString()));
    }

    @Test
    void unsuccessfulResultCanOmitTransactionIdAndDescription() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(PAYLOAD);
        body.put("input_ResultCode", "9");
        body.remove("input_TransactionID");
        body.remove("input_ResultDesc");
        var callback = new VodaMpesaSdk(config).parseCallback(body.toString());
        assertTrue(callback.isFailed());
        assertNull(callback.transactionId());
        assertNull(callback.resultDesc());
    }

    @Test
    void rejectsAmbiguousJsonAndWrongFieldTypesWithoutLeakingPayload() {
        var sdk = new VodaMpesaSdk(config);
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(PAYLOAD + " {}"));
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(PAYLOAD.replace("{", "{\"input_ResultCode\":\"9\",")));
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(PAYLOAD.replace("\"0\"", "0")));
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(PAYLOAD.replace("\"RvvsqB0rcP3Y\"", "[]")));
        var error = assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback("{private-payload"));
        assertFalse(error.getMessage().contains("private-payload"));
        assertNull(error.getCause());
        assertThrows(IllegalArgumentException.class, () -> sdk.parseCallback(null));
        assertThrows(NullPointerException.class, () -> sdk.handleCallback(PAYLOAD, null));
    }
}
