package tz.co.vodampesa.exception;

/**
 * @author Christopher oigo
 * HTTP, transport, protocol or provider failure. Never contains credentials or raw response bodies.
 */
public class VodaMpesaException extends RuntimeException {
    private final String responseCode;
    private final Integer httpStatus;

    public VodaMpesaException(String message, String responseCode, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.responseCode = responseCode;
        this.httpStatus = httpStatus;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
