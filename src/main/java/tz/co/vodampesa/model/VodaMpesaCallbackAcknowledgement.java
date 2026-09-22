package tz.co.vodampesa.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Callback receipt acknowledgement. Response code "0" acknowledges delivery, not payment success. */
public record VodaMpesaCallbackAcknowledgement(
        @JsonProperty("output_OriginalConversationID") String originalConversationId,
        @JsonProperty("output_ResponseCode") String responseCode,
        @JsonProperty("output_ResponseDesc") String responseDesc,
        @JsonProperty("output_ThirdPartyConversationID") String thirdPartyConversationId) {
}
