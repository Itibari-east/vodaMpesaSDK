package tz.co.vodampesa.model;

import tz.co.vodampesa.exception.VodaMpesaException;

import java.util.Objects;
import java.util.Optional;

/**
 * Fluent result: acceptance of a request is separate from completed settlement.
 */
public final class VodaMpesaResult {
    private final VodaMpesaResponse response;
    private final String requestConversationId;

    public VodaMpesaResult(VodaMpesaResponse response, String requestConversationId) {
        this.response = Objects.requireNonNull(response);
        this.requestConversationId = requestConversationId;
    }

    public boolean isAccepted() {
        return "INS-0".equals(response.responseCode());
    }

    /**
     * Alias for request acceptance only; use isCompleted() for final status queries.
     */
    public boolean isSuccess() {
        return isAccepted();
    }

    public boolean isCompleted() {
        return isAccepted() && "Completed".equalsIgnoreCase(response.transactionStatus());
    }

    public boolean isPending() {
        return "Pending".equalsIgnoreCase(response.transactionStatus());
    }

    public String getResponseCode() {
        return response.responseCode();
    }

    public String getResponseDesc() {
        return response.responseDesc();
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(response.transactionId());
    }

    public Optional<String> getConversationId() {
        return Optional.ofNullable(response.conversationId());
    }

    public Optional<String> getThirdPartyConversationId() {
        return Optional.ofNullable(response.thirdPartyConversationId());
    }

    public String getRequestConversationId() {
        return requestConversationId;
    }

    public Optional<String> getTransactionStatus() {
        return Optional.ofNullable(response.transactionStatus());
    }

    public VodaMpesaResponse getResponse() {
        return response;
    }

    public VodaMpesaResult throwIfFailed() {
        if (!isAccepted()) throw new VodaMpesaException("M-Pesa rejected the request", getResponseCode(), null, null);
        return this;
    }
}
