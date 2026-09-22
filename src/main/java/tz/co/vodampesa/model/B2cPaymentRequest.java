package tz.co.vodampesa.model;

/**
 * @author Christopher oigo
 * Immutable request. Amounts are positive whole TZS strings; conversation IDs are caller-controlled.
 */
public record B2cPaymentRequest(String amount, String customerMsisdn, String transactionReference,
                                String thirdPartyConversationId, String paymentItemsDesc) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String amount;
        private String customerMsisdn;
        private String transactionReference;
        private String thirdPartyConversationId;
        private String paymentItemsDesc;

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

        public Builder paymentItemsDesc(String value) {
            this.paymentItemsDesc = value;
            return this;
        }

        public B2cPaymentRequest build() {
            return new B2cPaymentRequest(amount, customerMsisdn, transactionReference, thirdPartyConversationId, paymentItemsDesc);
        }
    }
}
