package tz.co.vodampesa.model;

/**
 * @author Christopher oigo
 * Immutable request. Amounts are positive whole TZS strings; conversation IDs are caller-controlled.
 */
public record ReversalRequest(String transactionId, String reversalAmount, String thirdPartyConversationId,
                              String securityCredential, String initiatorIdentifier) {
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "ReversalRequest[redacted]";
    }

    public static final class Builder {
        private String transactionId;
        private String reversalAmount;
        private String thirdPartyConversationId;
        private String securityCredential;
        private String initiatorIdentifier;

        public Builder transactionId(String value) {
            this.transactionId = value;
            return this;
        }

        public Builder reversalAmount(String value) {
            this.reversalAmount = value;
            return this;
        }

        public Builder thirdPartyConversationId(String value) {
            this.thirdPartyConversationId = value;
            return this;
        }

        public Builder securityCredential(String value) {
            this.securityCredential = value;
            return this;
        }

        public Builder initiatorIdentifier(String value) {
            this.initiatorIdentifier = value;
            return this;
        }

        public ReversalRequest build() {
            return new ReversalRequest(transactionId, reversalAmount, thirdPartyConversationId, securityCredential, initiatorIdentifier);
        }
    }
}
