package tz.co.vodampesa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import tz.co.vodampesa.model.*;
import tz.co.vodampesa.model.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @author Christopher oigo
 * Validates requests and translates Java field names into the PDF's exact wire fields.
 */
public final class VodaMpesaService {
    private static final JsonMapper CALLBACK_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final VodaMpesaConfig config;
    private final VodaMpesaClient client;

    public VodaMpesaService(VodaMpesaConfig config) {
        this.config = new VodaMpesaConfig(config);
        this.client = new VodaMpesaClient(this.config);
    }

    private static String amount(String amount) {
        if (amount == null || !amount.matches("[0-9]+") || amount.chars().allMatch(c -> c == '0'))
            throw new IllegalArgumentException("amount must be a positive whole TZS value");
        return amount;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public boolean initialize() {
        client.initialize();
        return true;
    }

    public boolean isInitialized() {
        return client.isInitialized();
    }

    public void invalidateSession() {
        client.invalidateSession();
    }

    /**
     * Parses the callback PDF's input_* envelope without making an API call.
     * Parsing does not authenticate the sender. Verify inbound requests at your HTTP boundary.
     *
     * @param json raw JSON callback body
     * @return validated callback, with the original correlation identifiers
     * @throws IllegalArgumentException for malformed JSON or invalid documented fields
     */
    public VodaMpesaCallback parseCallback(String json) {
        required(json, "callback JSON");
        try {
            JsonNode body = CALLBACK_MAPPER.readTree(json);
            if (body == null || !body.isObject()) {
                throw new IllegalArgumentException("Callback must be a JSON object");
            }
            return new VodaMpesaCallback(
                    callbackField(body, "input_OriginalConversationID"),
                    callbackField(body, "input_ThirdPartyReference"),
                    callbackField(body, "input_TransactionID"),
                    callbackField(body, "input_ResultCode"),
                    callbackField(body, "input_ResultDesc"));
        } catch (JsonProcessingException e) {
            // Do not expose the raw payload through Jackson's exception/source context.
            throw new IllegalArgumentException("Callback must contain one valid JSON object without duplicate fields");
        }
    }

    /**
     * Validates and hands off a callback, then builds the PDF's acknowledgement.
     * The handler runs synchronously: durably save/enqueue the event quickly and do heavy work later.
     * The application must implement atomic, persistent deduplication and payment correlation.
     * Handler failures propagate; no acknowledgement is returned if the handoff fails.
     *
     * @param json raw JSON callback body
     * @param handler application's durable callback handoff (also receives failed transaction results)
     * @return body to serialize as JSON with HTTP 200 after successful handoff
     */
    public VodaMpesaCallbackAcknowledgement handleCallback(String json, Consumer<VodaMpesaCallback> handler) {
        Objects.requireNonNull(handler, "handler");
        VodaMpesaCallback callback = parseCallback(json);
        handler.accept(callback);
        return new VodaMpesaCallbackAcknowledgement(callback.originalConversationId(), "0",
                "Successfully Accepted Result", callback.thirdPartyReference());
    }

    private static String callbackField(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException(name + " must be a string");
        return value.textValue();
    }

    public VodaMpesaResult c2bPayment(C2bPaymentRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> body = payment(request.amount(), request.transactionReference(), request.thirdPartyConversationId());
        body.put("input_CustomerMSISDN", phone(request.customerMsisdn()));
        body.put("input_ServiceProviderCode", config.getServiceProviderCode());
        body.put("input_PurchasedItemsDesc", required(request.purchasedItemsDesc(), "purchasedItemsDesc"));
        return send(config.getC2bPath(), "POST", body);
    }

    public VodaMpesaResult b2cPayment(B2cPaymentRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> body = payment(request.amount(), request.transactionReference(), request.thirdPartyConversationId());
        body.put("input_CustomerMSISDN", phone(request.customerMsisdn()));
        body.put("input_ServiceProviderCode", config.getServiceProviderCode());
        body.put("input_PaymentItemsDesc", required(request.paymentItemsDesc(), "paymentItemsDesc"));
        return send(config.getB2cPath(), "POST", body);
    }

    public VodaMpesaResult b2bPayment(B2bPaymentRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> body = payment(request.amount(), request.transactionReference(), request.thirdPartyConversationId());
        body.put("input_PrimaryPartyCode", config.getServiceProviderCode());
        body.put("input_ReceiverPartyCode", required(request.receiverPartyCode(), "receiverPartyCode"));
        body.put("input_PurchasedItemsDesc", required(request.purchasedItemsDesc(), "purchasedItemsDesc"));
        return send(config.getB2bPath(), "POST", body);
    }

    public VodaMpesaResult reverseTransaction(ReversalRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> body = common(request.thirdPartyConversationId());
        body.put("input_TransactionID", required(request.transactionId(), "transactionId"));
        body.put("input_ServiceProviderCode", config.getServiceProviderCode());
        if (request.reversalAmount() != null) body.put("input_ReversalAmount", amount(request.reversalAmount()));
        if (request.securityCredential() != null)
            body.put("input_SecurityCredential", required(request.securityCredential(), "securityCredential"));
        if (request.initiatorIdentifier() != null)
            body.put("input_InitiatorIdentifier", required(request.initiatorIdentifier(), "initiatorIdentifier"));
        return send(config.getReversalPath(), "POST", body);
    }

    public VodaMpesaResult queryTransactionStatus(QueryStatusRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> body = common(request.thirdPartyConversationId());
        body.put("input_QueryReference", required(request.queryReference(), "queryReference"));
        body.put("input_ServiceProviderCode", config.getServiceProviderCode());
        return send(config.getQueryStatusPath(), config.getQueryMethod().name(), body);
    }

    /**
     * Reconciles a previous payment response with the current M-Pesa transaction status.
     * The previous response code is context only; even INS-0 requires a status query.
     * This method does not issue another payment or prompt for a PIN.
     *
     * @param responseCode response code previously received for the payment
     * @param transactionId M-Pesa transaction ID associated with the application's saved payment
     * @return verification containing both the previous code and the fresh query result
     */
    public VodaMpesaPaymentVerification verifyPayment(String responseCode, String transactionId) {
        required(responseCode, "responseCode");
        required(transactionId, "transactionId");
        VodaMpesaResult status = queryTransactionStatus(
                new QueryStatusRequest(transactionId, UUID.randomUUID().toString()));
        return new VodaMpesaPaymentVerification(responseCode, transactionId, status);
    }

    private Map<String, String> common(String conversationId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("input_Country", "TZN");
        body.put("input_ThirdPartyConversationID", required(conversationId, "thirdPartyConversationId"));
        return body;
    }

    private Map<String, String> payment(String amount, String reference, String conversationId) {
        Map<String, String> body = common(conversationId);
        body.put("input_Amount", amount(amount));
        body.put("input_Currency", "TZS");
        required(reference, "transactionReference");
        if (reference.length() > 20) throw new IllegalArgumentException("transactionReference must be 1–20 characters");
        body.put("input_TransactionReference", reference);
        return body;
    }

    private VodaMpesaResult send(String path, String method, Map<String, String> body) {
        return new VodaMpesaResult(client.send(path, method, body), body.get("input_ThirdPartyConversationID"));
    }

    private String phone(String phone) {
        required(phone, "customerMsisdn");
        if (phone.matches("255[0-9]{9}") || (!config.isProduction() && "000000000001".equals(phone))) return phone;
        throw new IllegalArgumentException("customerMsisdn must contain 255 followed by 9 digits (sandbox also accepts 000000000001)");
    }
}
