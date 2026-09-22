package tz.co.vodampesa.model;

/**
 * @author Christopher oigo
 * Immutable request. Amounts are positive whole TZS strings; conversation IDs are caller-controlled.
 */
public record C2bPaymentRequest(String amount, String customerMsisdn, String transactionReference,
                                String thirdPartyConversationId, String purchasedItemsDesc) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String amount;
        private String customerMsisdn;
        private String transactionReference;
        private String thirdPartyConversationId;
        private String purchasedItemsDesc;

        public Builder amount(String value) {
            this.amount = value;
            return this;
        }

        public Builder customerMsisdn(String value) {
            this.customerMsisdn = value;
            return this;
        }

        public Builder transactionReference(String value) {
            this.transactionReference = value;
            return this;
        }

        public Builder thirdPartyConversationId(String value) {
            this.thirdPartyConversationId = value;
            return this;
        }

        public Builder purchasedItemsDesc(String value) {
            this.purchasedItemsDesc = value;
            return this;
        }

        public C2bPaymentRequest build() {
            return new C2bPaymentRequest(amount, customerMsisdn, transactionReference, thirdPartyConversationId, purchasedItemsDesc);
        }
    }
}
