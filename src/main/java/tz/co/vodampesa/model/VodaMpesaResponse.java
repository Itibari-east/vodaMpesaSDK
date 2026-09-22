package tz.co.vodampesa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Provider response envelope. Unknown fields are accepted for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodaMpesaResponse(
        @JsonProperty("output_ResponseCode") String responseCode,
        @JsonProperty("output_ResponseDesc") String responseDesc,
        @JsonProperty("output_TransactionID") String transactionId,
        @JsonProperty("output_ConversationID") String conversationId,
        @JsonProperty("output_ThirdPartyConversationID") String thirdPartyConversationId,
        @JsonProperty("output_ResponseTransactionStatus") String transactionStatus) {
}
