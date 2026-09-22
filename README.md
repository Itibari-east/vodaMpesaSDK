# Vodacom M-Pesa Tanzania Java SDK

A Java 17 Maven library for the endpoints in `M-Pesa_Tanzania_OpenAPI_Full_Documentation.pdf`.
It follows eTIMS SDK's `Sdk → Service → Client`, configuration bean, request models, fluent result,
exception, optional Spring Boot auto-configuration, and Maven source/Javadoc/release layout. **One account per SDK; no
tenant configuration or registry.**

The core uses Java's HTTP client and Jackson. Spring is optional; this is a normal dependency JAR,
not an executable Spring Boot application. Request DTOs are immutable Java records with builders.

## Installation

This version is a local development snapshot, **not published to Maven Central**.
Install it into your local Maven repository first:

```bash
./mvnw clean install
# Or use an installed Maven: mvn clean install
```

Then add:

```xml

<dependency>
    <groupId>io.github.montella-03</groupId>
    <artifactId>vodampesa-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Java 17+ is required. The optional Spring integration targets Spring Boot 3.x, matching eTIMS SDK.

## Quick start: plain Java

```java
import tz.co.vodampesa.VodaMpesaConfig;
import tz.co.vodampesa.VodaMpesaSdk;
import model.tz.co.vodampesa.VodaMpesaResult;

VodaMpesaConfig config = new VodaMpesaConfig();
config.

setApiKey(System.getenv("MPESA_API_KEY"));
        config.

setPublicKey(System.getenv("MPESA_PUBLIC_KEY")); // X.509 PUBLIC KEY PEM or Base64
        config.

setOrigin(System.getenv("MPESA_ORIGIN"));       // exact value from your portal
        config.

setServiceProviderCode("000000");               // your sandbox short code
config.

setProduction(false);

VodaMpesaSdk sdk = new VodaMpesaSdk(config);
// Optional: sdk.initialize(); authentication otherwise happens on the first valid request.

VodaMpesaResult result = sdk.collectPayment(
        "000000000001", 1500, "ORD-98765", "Payment for order 98765");
result.

throwIfFailed();

// INS-0 means request accepted. It does NOT mean the customer's payment completed.
String transactionId = result.getTransactionId().orElseThrow();
VodaMpesaResult status = sdk.queryTransactionStatus(transactionId);
if(status.

isCompleted()){
        // Record confirmed payment in your application.
        }
```

Reuse the SDK instance across requests and threads. Configuration is copied at construction;
create another instance to change account or environment. Amounts are whole TZS; convenience
methods accept `long`, avoiding floating-point rounding. Phone numbers must be `255` plus nine
digits. The sandbox also accepts `000000000001`; local phone formats are not silently rewritten.

## Endpoints and methods

| PDF endpoint                   | SDK method                                   | Convenient equivalent                                          |
|--------------------------------|----------------------------------------------|----------------------------------------------------------------|
| `GET getSession/`              | `initialize()`                               | Called lazily and cached automatically                         |
| `POST c2bPayment/singleStage/` | `c2bPayment(C2bPaymentRequest)`              | `collectPayment(msisdn, amount, reference, description)`       |
| `POST b2cPayment/`             | `b2cPayment(B2cPaymentRequest)`              | `sendToCustomer(msisdn, amount, reference, description)`       |
| `POST b2bPayment/`             | `b2bPayment(B2bPaymentRequest)`              | `sendToBusiness(receiverCode, amount, reference, description)` |
| `POST reversal`                | `reverseTransaction(ReversalRequest)`        | `reverseTransaction(transactionId, amount)`                    |
| `POST queryTransactionStatus/` | `queryTransactionStatus(QueryStatusRequest)` | `queryTransactionStatus(queryReference)`                       |

Country `TZN`, currency `TZS`, and your account code are populated automatically. B2B uses your
configured service provider code as `input_PrimaryPartyCode`. B2C uses `input_PaymentItemsDesc`;
C2B/B2B use `input_PurchasedItemsDesc`, as specified in the PDF.

## Recommended payment workflow

For production integrations, use a request builder and **persist its conversation ID before sending**.
Convenience methods create UUIDs internally; `result.getRequestConversationId()` returns that ID
on a successful HTTP response, but no result exists if the connection fails. A saved ID lets your
application reconcile an ambiguous outcome without submitting another debit.

```java
import model.tz.co.vodampesa.C2bPaymentRequest;

String conversationId = VodaMpesaSdk.newConversationId();
C2bPaymentRequest request = C2bPaymentRequest.builder()
        .amount("1500")
        .customerMsisdn("255712345678")
        .transactionReference("ORD-98765") // 1–20 characters; unique within your application
        .thirdPartyConversationId(conversationId)
        .purchasedItemsDesc("Payment for order 98765")
        .build();

// Save conversationId, reference, amount and a pending payment record in your database here.
VodaMpesaResult result = sdk.c2bPayment(request);
// Save response identifiers and response code, including rejected requests.
result.

throwIfFailed();
```

The SDK validates required fields and amounts before authentication/network calls. It does not
provide durable storage, enforce reference uniqueness, or promise provider-side idempotency.
It sends each transaction once: no automatic retries on HTTP errors, auth rejection, or timeouts.
An auth rejection invalidates the session for the next explicit call; it does not replay the payment.
A transport failure can leave the outcome unknown. Reconcile using a saved provider transaction ID
or conversation reference before deciding whether a new submission is appropriate.

Poll status at an application-controlled interval as a fallback for missed callbacks. The SDK also
supports the final result envelope and acknowledgement from `M-Pesa_Tanzania_Callbacks_Documentation.pdf`.

## Verify a payment after a failure

Use `verifyPayment(responseCode, transactionId)` when your application received an earlier failure
or uncertain response and the customer asks you to check their payment:

```java
var verification = sdk.verifyPayment("INS-9", "RvvsqB0rcP3Y");
if (verification.isVerified()) {
    // M-Pesa reports Completed for this exact transaction.
    // Atomically reconcile the saved payment; avoid fulfilling the same order twice.
} else {
    var status = verification.statusResult();
    // Inspect status.getResponseCode() and status.getTransactionStatus().
    // Pending, rejected queries, unknown states, or missing/mismatched IDs are not verified.
}
```

With an existing `VodaMpesaResponse`, pass its `responseCode()` and `transactionId()` accessors.
Both arguments must be nonblank. The previous response code is retained in
`verification.originalResponseCode()` as context; the fresh query is the source of truth, including
when the previous code was `INS-0`. This method uses the existing Query Transaction Status endpoint
and respects the configured GET/POST method. It sends no new payment or customer PIN prompt.

`isVerified()` requires a successful status query (`INS-0`), status `Completed`, and an exact match
between the returned and requested transaction IDs. `false` means **completion is not confirmed**;
it does not imply the payment failed or that it is safe to submit another debit. Query transport
errors propagate as `VodaMpesaException` and should leave the payment unresolved.

The SDK has no payments database. Before calling this from a customer-facing endpoint, load the
customer's saved payment and check ownership and its associated transaction ID. Do not accept an
arbitrary completed transaction as proof of payment for another order. The status response in the
PDF has no amount/order fields, so that association must come from your saved payment records.
If no transaction ID was received, use `queryTransactionStatus(savedConversationReference)` for
reconciliation instead. Customer re-authorization of a failed transaction is not a documented API.

## Callbacks / webhooks

Register your application's publicly reachable HTTPS Response URL in the M-Pesa portal. The SDK
provides `parseCallback(json)` and `handleCallback(json, handler)` through both `VodaMpesaSdk` and
`VodaMpesaService`; your application owns the HTTP endpoint and persistence.

```java
VodaMpesaCallbackAcknowledgement acknowledgement = sdk.handleCallback(rawJson, callback -> {
    // Durably save or enqueue the callback here, using your application's repository/queue.
    // Correlate callback.thirdPartyReference() with the request's thirdPartyConversationId.
    // Also verify callback.originalConversationId() against the saved provider conversation ID.
    // callback.isSuccess(): input_ResultCode is exactly "0" (not "INS-0").
    // callback.transactionId(), resultCode(), resultDesc() preserve the provider's values.
});
// Serialize acknowledgement as JSON and return HTTP 200 from your endpoint.
```

The lambda above describes the integration point; replace the comments with a durable handoff
before using it. A Spring MVC application can expose it as follows (Spring Web is supplied by
the consuming application):

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.vodampesa.VodaMpesaSdk;
import model.tz.co.vodampesa.VodaMpesaCallback;
import model.tz.co.vodampesa.VodaMpesaCallbackAcknowledgement;

@RestController
@RequestMapping("/api/mpesa")
class MpesaCallbackController {
  private final VodaMpesaSdk sdk;
  private final CallbackInbox inbox;

  MpesaCallbackController(VodaMpesaSdk sdk, CallbackInbox inbox) {
    this.sdk = sdk;
    this.inbox = inbox;
  }

  @PostMapping(value = "/callback", consumes = "application/json", produces = "application/json")
  ResponseEntity<VodaMpesaCallbackAcknowledgement> callback(@RequestBody String json) {
    return ResponseEntity.ok(sdk.handleCallback(json, inbox::saveIfAbsent));
  }

  // Implement as an application bean backed by a database or durable queue.
  // Return only after commit; existing duplicates are a successful no-op.
  interface CallbackInbox {
    void saveIfAbsent(VodaMpesaCallback callback);
  }
}
```

`handleCallback` validates the payload, calls your handler synchronously, then returns exactly:

```json
{
  "output_OriginalConversationID": "<input_OriginalConversationID>",
  "output_ResponseCode": "0",
  "output_ResponseDesc": "Successfully Accepted Result",
  "output_ThirdPartyConversationID": "<input_ThirdPartyReference>"
}
```

Acknowledgement code `"0"` confirms receipt even when the **transaction failed**. Callback
`input_ResultCode = "0"` means transaction success; every other nonblank code is exposed as failed.
The PDF does not define distinct numeric mappings for cancelled versus timed-out results, so those
are not guessed from descriptions. This callback model is separate from synchronous `INS-0` responses.

The handler should commit an inbox entry quickly, then a worker can update the payment and perform
fulfilment. Handler exceptions propagate and no acknowledgement is returned; let your HTTP layer
return an error on storage failure so delivery can be retried. Map malformed input
(`IllegalArgumentException` from parsing) to HTTP 400 in your application's exception handling.

M-Pesa may send duplicates: the SDK deliberately forwards every delivery to your handler. Use a
database uniqueness constraint and an atomic state transition, not an in-memory flag. Match the saved
request reference and conversation, retain the final transaction ID, and prevent duplicate fulfilment
or a later conflicting delivery from overwriting an already confirmed result. Keep unknown or conflicting
references for reconciliation. Heavy processing belongs after durable receipt; missed callbacks can
still be reconciled with `queryTransactionStatus`.

The parser requires nonblank original conversation ID, third-party reference and result code.
Successful callbacks also require a transaction ID; failures may omit it, and description is optional.
Present fields must be strings, matching the PDF. Unknown fields are ignored; duplicate JSON keys,
trailing JSON values, malformed bodies and wrong field types are rejected before invoking the handler.
Parsing/handling makes no outbound request and does not initialize a session.

The callback PDF supplies no signature or inbound authentication scheme. Parsing is **not sender
verification**: enforce the portal's actual controls at your HTTP boundary before accepting events.
The SDK does not log raw payloads or headers; apply your application's audit and redaction policy.

## Disbursements, reversals and status

```java
sdk.sendToCustomer("255712345678",5000,"SAL-202609-001","September salary")
        .

throwIfFailed();
sdk.

sendToBusiness("654321",10000,"SETTLE-2026-09","Supplier settlement")
        .

throwIfFailed();
sdk.

reverseTransaction("RvvsqB0rcP3Y",1500).

throwIfFailed();

VodaMpesaResult status = sdk.queryTransactionStatus("RvvsqB0rcP3Y");
```

For reversal credentials required by some portal versions:

```java
import model.tz.co.vodampesa.ReversalRequest;

ReversalRequest request = ReversalRequest.builder()
        .transactionId("RvvsqB0rcP3Y")
        .reversalAmount("1500")
        .thirdPartyConversationId(VodaMpesaSdk.newConversationId())
        .securityCredential(System.getenv("MPESA_SECURITY_CREDENTIAL"))
        .initiatorIdentifier(System.getenv("MPESA_INITIATOR"))
        .build();
sdk.

reverseTransaction(request).

throwIfFailed();
```

Omitting `reversalAmount` omits `input_ReversalAmount` from JSON. Only do this if your portal
explicitly supports full reversals without an amount. The SDK does not determine the original
transaction's refundable balance or infer whether a partial reversal is available for your account.

## Spring Boot

Sandbox `application.yaml`:

```yaml
vodampesa:
  api-key: ${MPESA_API_KEY}
  public-key: ${MPESA_PUBLIC_KEY}
  origin: ${MPESA_ORIGIN}
  service-provider-code: "000000"
  production: false
  auto-initialize: false
```

Production `application.yaml`:

```yaml
vodampesa:
  enabled: true
  production: true
  api-key: ${MPESA_API_KEY}
  public-key: ${MPESA_PUBLIC_KEY}
  origin: ${MPESA_ORIGIN}
  service-provider-code: "${MPESA_SERVICE_PROVIDER_CODE}"
  auto-initialize: true
  connect-timeout: 10s
  request-timeout: 60s
  session-lifetime: ${MPESA_SESSION_LIFETIME:23h}
```

Supply your **production** API key, RSA public key, portal Origin value, and business short code
through these environment variables. `MPESA_PUBLIC_KEY` accepts an X.509 PEM public key with real
line breaks or its Base64 body. Set `MPESA_SESSION_LIFETIME` below the session lifetime configured
in your portal; `23h` is the SDK default, not a guaranteed provider lifetime.

`production: true` selects `https://openapi.m-pesa.com/openapi/ipg/v2/vodacomTZN/` automatically;
no `base-url` override is needed. With `auto-initialize: true`, startup authenticates with M-Pesa
and fails if authentication fails. Set it to `false` if you prefer authentication on the first request.
Configure your public HTTPS callback URL separately in the M-Pesa portal, as described above.

Inject `VodaMpesaSdk` directly into your service; no `@Import` or manual `@Bean` is needed:

```java

@Service
public class PaymentService {
    private final VodaMpesaSdk mpesa;

    public PaymentService(VodaMpesaSdk mpesa) {
        this.mpesa = mpesa;
    }

    public VodaMpesaResult collect(String phone, long amount, String orderReference) {
        return mpesa.collectPayment(phone, amount, orderReference, "Order payment");
    }
}
```

Set `vodampesa.enabled=false` to disable auto-configuration. A custom `VodaMpesaSdk` bean takes
precedence. Missing credentials fail startup when enabled; network authentication is lazy by default.
With `auto-initialize=true`, an authentication failure fails startup rather than silently leaving an
unusable integration. No `tenants` or `default-tenant` settings exist.

## Result and error handling

| Method                                       | Meaning                                               |
|----------------------------------------------|-------------------------------------------------------|
| `isAccepted()` / `isSuccess()`               | Provider returned `INS-0`; only request acceptance    |
| `isCompleted()`                              | Accepted response with transaction status `Completed` |
| `isPending()`                                | Provider reports transaction status `Pending`         |
| `getResponseCode()` / `getResponseDesc()`    | Provider business result                              |
| `getTransactionId()` / `getConversationId()` | Optional provider identifiers                         |
| `getThirdPartyConversationId()`              | Optional identifier echoed by the provider            |
| `getRequestConversationId()`                 | Exact identifier sent by the SDK                      |
| `getTransactionStatus()`                     | Optional raw status, including future values          |
| `throwIfFailed()`                            | Throws for non-`INS-0`; does not assert settlement    |
| `getResponse()`                              | Parsed provider response envelope                     |

```java
import exception.tz.co.vodampesa.VodaMpesaException;

try{
VodaMpesaResult result = sdk.queryTransactionStatus("RvvsqB0rcP3Y");
    result.

throwIfFailed();
}catch(
VodaMpesaException ex){
        // ex.getResponseCode(): provider code when available
        // ex.getHttpStatus(): HTTP status when available
        // ex.getMessage(): SDK failure description, without raw request/response credentials
        }
```

Invalid caller configuration/fields throw `IllegalArgumentException`; null request objects throw
`NullPointerException`. HTTP, transport, authentication and protocol failures throw `VodaMpesaException`.
Business rejections on HTTP 2xx return a result, so callers can inspect codes such as `INS-5` or `INS-10`.

## Configuration reference

Java setters correspond to the following `vodampesa.*` Spring properties.

| Property                                                   | Default                   | Purpose                                                                    |
|------------------------------------------------------------|---------------------------|----------------------------------------------------------------------------|
| `api-key`, `public-key`, `origin`, `service-provider-code` | Required                  | Portal/account configuration                                               |
| `production`                                               | `false`                   | Select production instead of sandbox                                       |
| `base-url`                                                 | Derived from environment  | Optional portal URL override; HTTPS required except sandbox loopback tests |
| `connect-timeout`                                          | `10s`                     | Connection establishment timeout                                           |
| `request-timeout`                                          | `60s`                     | Per-request timeout                                                        |
| `session-lifetime`                                         | `23h`                     | Local cache lifetime; set below the actual portal lifetime                 |
| `rsa-padding`                                              | `OAEP_SHA256`             | Alternatives: `OAEP_SHA1`, `PKCS1`                                         |
| `query-method`                                             | `POST`                    | Alternative: `GET`, with URL-encoded query parameters and no body          |
| `session-path`                                             | `getSession/`             | Relative authentication path                                               |
| `c2b-path`                                                 | `c2bPayment/singleStage/` | Relative collection path                                                   |
| `b2c-path`                                                 | `b2cPayment/`             | May need `b2cPayment/singleStage/`                                         |
| `b2b-path`                                                 | `b2bPayment/`             | Relative business transfer path                                            |
| `reversal-path`                                            | `reversal`                | Relative reversal path                                                     |
| `query-status-path`                                        | `queryTransactionStatus/` | Relative status path                                                       |
| `enabled`                                                  | `true`                    | Spring integration only                                                    |
| `auto-initialize`                                          | `false`                   | Spring startup authentication                                              |

Base URLs from the PDF:

- Sandbox: `https://openapi.m-pesa.com/sandbox/ipg/v2/vodacomTZN/`
- Production: `https://openapi.m-pesa.com/openapi/ipg/v2/vodacomTZN/`

Endpoint overrides must be relative paths without queries, fragments or traversal. Redirects are not followed.
`initialize()` reuses a valid session; `invalidateSession()` discards it, and the next call authenticates again.
Session refresh is synchronized so concurrent first requests share one authentication operation.

### Portal details to verify

The supplied PDF identifies itself as a compiled reference, not a definitive portal specification.
Its defaults are implemented here, with explicit configuration for its ambiguities:

- RSA defaults to OAEP using SHA-256 **for both the OAEP digest and MGF1**. SHA-1 OAEP and PKCS#1 v1.5
  are explicit alternatives, not automatic fallbacks. Confirm the exact padding with your portal.
- The PDF omits the session response schema. This implementation accepts `output_SessionID` or
  `output_SessionKey`, alongside `output_ResponseCode = INS-0`; verify using a sandbox response.
- Session duration, B2C path, query HTTP method, reversal credentials and optional reversal amount
  depend on your portal application. No live endpoint compatibility is claimed by the local tests.
- Callback fields and acknowledgements follow the separate callbacks PDF. Inbound authentication is
  not specified; verify the portal's actual controls before deploying the endpoint.

## Project structure

```text
src/main/java/tz/co/vodampesa/
├── VodaMpesaSdk.java
├── VodaMpesaConfig.java
├── VodaMpesaService.java
├── VodaMpesaClient.java
├── exception/VodaMpesaException.java
├── model/
│   ├── C2bPaymentRequest.java
│   ├── B2cPaymentRequest.java
│   ├── B2bPaymentRequest.java
│   ├── ReversalRequest.java
│   ├── QueryStatusRequest.java
│   ├── VodaMpesaCallback.java
│   ├── VodaMpesaCallbackAcknowledgement.java
│   ├── VodaMpesaResponse.java
│   └── VodaMpesaResult.java
└── spring/
    ├── VodaMpesaProperties.java
    └── VodaMpesaAutoConfiguration.java
```

## Build and release

`mvn clean verify` runs tests against a localhost mock API, including actual RSA encryption,
HTTP paths/headers/payloads, session caching/expiry/concurrency, errors, and Spring configuration.
It produces the main JAR, sources JAR and Javadoc JAR. No M-Pesa credentials or live transactions
are required. `mvn install` additionally makes these available to local consumers.


## License

MIT — see [LICENSE](LICENSE).
