package tz.co.vodampesa.model;

import java.util.Objects;

/**
 * Reconciliation of an earlier payment response with a fresh transaction status query.
 * A false verification means completion has not been confirmed, not necessarily that payment failed.
 */
public record VodaMpesaPaymentVerification(
        String originalResponseCode,
        String transactionId,
        VodaMpesaResult statusResult) {

    public VodaMpesaPaymentVerification {
        if (originalResponseCode == null || originalResponseCode.isBlank())
            throw new IllegalArgumentException("originalResponseCode is required");
        if (transactionId == null || transactionId.isBlank())
            throw new IllegalArgumentException("transactionId is required");
        Objects.requireNonNull(statusResult, "statusResult");
    }

    /** True only if M-Pesa accepted the query and reported Completed for the requested transaction. */
    public boolean isVerified() {
        return statusResult.isCompleted() && isTransactionMatched();
    }

    /** Missing or mismatched provider transaction IDs cannot confirm the requested payment. */
    public boolean isTransactionMatched() {
        return statusResult.getTransactionId().filter(transactionId::equals).isPresent();
    }
}
