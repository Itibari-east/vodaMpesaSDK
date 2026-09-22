package tz.co.vodampesa;

import tz.co.vodampesa.model.*;
import tz.co.vodampesa.model.*;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Single-account SDK entry point. Reuse one instance across threads.
 */
public final class VodaMpesaSdk {
    private final VodaMpesaService service;

    public VodaMpesaSdk(VodaMpesaConfig config) {
        service = new VodaMpesaService(config);
    }

    public static String newConversationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Optional warm-up: otherwise authentication occurs on the first request.
     */
    public boolean initialize() {
        return service.initialize();
    }

    public boolean isInitialized() {
        return service.isInitialized();
    }

    public void invalidateSession() {
        service.invalidateSession();
    }

    /**
     * Parses a callback without authenticating its sender or making an outbound request.
     * @param json raw JSON callback body
     * @return validated final transaction result
     */
    public VodaMpesaCallback parseCallback(String json) {
        return service.parseCallback(json);
    }

    /**
     * Hands off a callback to the application's durable, idempotent handler and returns its acknowledgement.
     * Handler exceptions propagate so a failed handoff is not acknowledged.
     * @param json raw JSON callback body
     * @param handler fast persistence or durable queue operation; performs no automatic deduplication
     * @return JSON response body to send with HTTP 200
     * @see VodaMpesaService#handleCallback(String, Consumer)
     */
    public VodaMpesaCallbackAcknowledgement handleCallback(String json, Consumer<VodaMpesaCallback> handler) {
        return service.handleCallback(json, handler);
    }

    public VodaMpesaResult c2bPayment(C2bPaymentRequest request) {
        return service.c2bPayment(request);
    }

    public VodaMpesaResult b2cPayment(B2cPaymentRequest request) {
        return service.b2cPayment(request);
    }

    public VodaMpesaResult b2bPayment(B2bPaymentRequest request) {
        return service.b2bPayment(request);
    }

    public VodaMpesaResult reverseTransaction(ReversalRequest request) {
        return service.reverseTransaction(request);
    }

    public VodaMpesaResult queryTransactionStatus(QueryStatusRequest request) {
        return service.queryTransactionStatus(request);
    }

    /**
     * Checks a payment against M-Pesa after an earlier failure or uncertain outcome.
     * Verifies completion only for the same transaction ID; never submits another payment.
     * @param responseCode earlier payment response code, retained as context
     * @param transactionId transaction ID linked to the application's saved payment
     * @return fresh status and verification decision
     */
    public VodaMpesaPaymentVerification verifyPayment(String responseCode, String transactionId) {
        return service.verifyPayment(responseCode, transactionId);
    }

    /**
     * Requests a USSD payment. INS-0 means accepted, not settled.
     */
    public VodaMpesaResult collectPayment(String customerMsisdn, long amount, String reference, String description) {
        return c2bPayment(new C2bPaymentRequest(Long.toString(amount), customerMsisdn, reference, newConversationId(), description));
    }

    public VodaMpesaResult sendToCustomer(String customerMsisdn, long amount, String reference, String description) {
        return b2cPayment(new B2cPaymentRequest(Long.toString(amount), customerMsisdn, reference, newConversationId(), description));
    }

    public VodaMpesaResult sendToBusiness(String receiverPartyCode, long amount, String reference, String description) {
        return b2bPayment(new B2bPaymentRequest(Long.toString(amount), receiverPartyCode, reference, newConversationId(), description));
    }

    public VodaMpesaResult reverseTransaction(String transactionId, long amount) {
        return reverseTransaction(new ReversalRequest(transactionId, Long.toString(amount), newConversationId(), null, null));
    }

    public VodaMpesaResult queryTransactionStatus(String queryReference) {
        return queryTransactionStatus(new QueryStatusRequest(queryReference, newConversationId()));
    }
}
