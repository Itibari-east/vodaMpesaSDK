package tz.co.vodampesa.model;

/**
 * @author Christopher oigo
 * Immutable request. Amounts are positive whole TZS strings; conversation IDs are caller-controlled.
 */
public record QueryStatusRequest(String queryReference, String thirdPartyConversationId) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String queryReference;
        private String thirdPartyConversationId;

        public Builder queryReference(String value) {
            this.queryReference = value;
            return this;
        }

        public Builder thirdPartyConversationId(String value) {
            this.thirdPartyConversationId = value;
            return this;
        }

        public QueryStatusRequest build() {
            return new QueryStatusRequest(queryReference, thirdPartyConversationId);
        }
    }
}
