package tz.co.vodampesa.exception;

import tz.co.vodampesa.model.VodaMpesaResponse;

/**
 * SDK-created failures contain no credentials or raw request/response bodies.
 * Correlation identifiers and provider descriptions are intended for internal diagnostics;
 * applications should control which details they expose to customers.
 */
public class VodaMpesaException extends RuntimeException {
    /** Stable categories: callers should branch on these instead of parsing message text. */
    public enum Category {
        /** Session rejection, encryption failure, or HTTP 401/403. */
        AUTHENTICATION,
        /** Other non-success HTTP status. */
        HTTP,
        /** Connection or response deadline exceeded; consult stage and outcomeUnknown. */
        TIMEOUT,
        /** Network I/O failure or interrupted request. */
        TRANSPORT,
        /** Invalid JSON, missing required response fields, or invalid session envelope. */
        PROTOCOL,
        /** Business response converted to an exception by throwIfFailed(). */
        PROVIDER,
        /** Failure created with the legacy constructor. */
        UNKNOWN
    }

    /** Authentication occurs before the business request is submitted. */
    public enum Stage { AUTHENTICATION, REQUEST, UNKNOWN }

    private final Category category;
    private final Stage stage;
    private final String responseCode;
    private final Integer httpStatus;
    private final String operation;
    private final String requestConversationId;
    private final VodaMpesaResponse response;
    private final boolean outcomeUnknown;

    /** Backward-compatible constructor for applications that already create SDK exceptions. */
    public VodaMpesaException(String message, String responseCode, Integer httpStatus, Throwable cause) {
        this(message, Category.UNKNOWN, Stage.UNKNOWN, responseCode, httpStatus, null, null, null, true, cause);
    }

    public VodaMpesaException(String message, Category category, Stage stage, String responseCode,
                             Integer httpStatus, String operation, String requestConversationId,
                             VodaMpesaResponse response, boolean outcomeUnknown, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.stage = stage;
        this.responseCode = responseCode;
        this.httpStatus = httpStatus;
        this.operation = operation;
        this.requestConversationId = requestConversationId;
        this.response = response;
        this.outcomeUnknown = outcomeUnknown;
    }

    public Category getCategory() { return category; }
    public Stage getStage() { return stage; }
    public String getResponseCode() { return responseCode; }
    public Integer getHttpStatus() { return httpStatus; }
    /**
     * Relative endpoint path, never a URL containing query parameters. During lazy
     * authentication this identifies the intended business operation; stage identifies
     * that authentication failed before the operation could be submitted.
     */
    public String getOperation() { return operation; }
    public String getRequestConversationId() { return requestConversationId; }
    /** Parsed provider fields only; never the raw response body. May be null. */
    public VodaMpesaResponse getResponse() { return response; }
    public String getResponseDesc() { return response == null ? null : response.responseDesc(); }
    public String getTransactionId() { return response == null ? null : response.transactionId(); }
    public String getConversationId() { return response == null ? null : response.conversationId(); }
    public String getThirdPartyConversationId() { return response == null ? null : response.thirdPartyConversationId(); }

    /**
     * True when settlement cannot be ruled out: reconcile before submitting another debit.
     * False is not a retry authorization (for example, status queries do not move money).
     */
    public boolean isOutcomeUnknown() { return outcomeUnknown; }
}
