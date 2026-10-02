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
    private final String operation;
    private final boolean statusQuery;

    public VodaMpesaResult(VodaMpesaResponse response, String requestConversationId) {
        this(response, requestConversationId, null, false);
    }

    /**
     * Retains the operation for throwIfFailed(); statusQuery identifies a read operation
     * even when the caller configured a custom endpoint path.
     */
    public VodaMpesaResult(VodaMpesaResponse response, String requestConversationId,
                           String operation, boolean statusQuery) {
        this.response = Objects.requireNonNull(response);
        this.operation = operation;
        this.statusQuery = statusQuery;
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

    /**
     * Converts a provider rejection into a structured exception, preserving correlation fields.
     * A rejection code alone does not establish final settlement; reconcile mutations first.
     */
    public VodaMpesaResult throwIfFailed() {
        if (!isAccepted()) throw new VodaMpesaException("M-Pesa rejected the request",
                VodaMpesaException.Category.PROVIDER, VodaMpesaException.Stage.REQUEST,
                getResponseCode(), null, operation, requestConversationId, response,
                !statusQuery, null);
        return this;
    }
}
