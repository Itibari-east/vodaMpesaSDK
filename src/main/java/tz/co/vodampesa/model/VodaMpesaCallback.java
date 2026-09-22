package tz.co.vodampesa.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Final result from the callbacks PDF. Result code "0", not "INS-0", means success. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodaMpesaCallback(
        @JsonProperty("input_OriginalConversationID") String originalConversationId,
        @JsonProperty("input_ThirdPartyReference") String thirdPartyReference,
        @JsonProperty("input_TransactionID") String transactionId,
        @JsonProperty("input_ResultCode") String resultCode,
        @JsonProperty("input_ResultDesc") String resultDesc) {

    /**
     * Requires correlation identifiers and a result code. A successful result also requires a
     * transaction ID; unsuccessful results may omit it. Description is optional.
     */
    public VodaMpesaCallback {
        required(originalConversationId, "input_OriginalConversationID");
        required(thirdPartyReference, "input_ThirdPartyReference");
        required(resultCode, "input_ResultCode");
        if ("0".equals(resultCode)) required(transactionId, "input_TransactionID for a successful callback");
    }

    @JsonIgnore
    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    /** The PDF groups all nonzero results together; inspect the raw code/description for details. */
    @JsonIgnore
    public boolean isFailed() {
        return !isSuccess();
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
