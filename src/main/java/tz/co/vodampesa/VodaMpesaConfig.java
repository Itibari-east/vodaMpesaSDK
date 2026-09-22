package tz.co.vodampesa;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Mutable setup bean. Each SDK takes a defensive snapshot at construction.
 */
public class VodaMpesaConfig {
    private String apiKey = null;
    private String publicKey = null;
    private String origin = null;
    private String serviceProviderCode = null;
    private boolean production = false;
    private String baseUrl = null;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(60);
    private Duration sessionLifetime = Duration.ofHours(23);
    private RsaPadding rsaPadding = RsaPadding.OAEP_SHA256;
    private String sessionPath = "getSession/";
    private String c2bPath = "c2bPayment/singleStage/";
    private String b2cPath = "b2cPayment/";
    private String b2bPath = "b2bPayment/";
    private String reversalPath = "reversal";
    private String queryStatusPath = "queryTransactionStatus/";
    private QueryMethod queryMethod = QueryMethod.POST;
    public VodaMpesaConfig() {
    }
    public VodaMpesaConfig(VodaMpesaConfig source) {
        this.apiKey = source.apiKey;
        this.publicKey = source.publicKey;
        this.origin = source.origin;
        this.serviceProviderCode = source.serviceProviderCode;
        this.production = source.production;
        this.baseUrl = source.baseUrl;
        this.connectTimeout = source.connectTimeout;
        this.requestTimeout = source.requestTimeout;
        this.sessionLifetime = source.sessionLifetime;
        this.rsaPadding = source.rsaPadding;
        this.sessionPath = source.sessionPath;
        this.c2bPath = source.c2bPath;
        this.b2cPath = source.b2cPath;
        this.b2bPath = source.b2bPath;
        this.reversalPath = source.reversalPath;
        this.queryStatusPath = source.queryStatusPath;
        this.queryMethod = source.queryMethod;
    }

    private static void positive(Duration d, String name) {
        if (d == null || d.isZero() || d.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String value) {
        this.apiKey = value;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String value) {
        this.publicKey = value;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String value) {
        this.origin = value;
    }

    public String getServiceProviderCode() {
        return serviceProviderCode;
    }

    public void setServiceProviderCode(String value) {
        this.serviceProviderCode = value;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean value) {
        this.production = value;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String value) {
        this.baseUrl = value;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration value) {
        this.connectTimeout = value;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration value) {
        this.requestTimeout = value;
    }

    public Duration getSessionLifetime() {
        return sessionLifetime;
    }

    public void setSessionLifetime(Duration value) {
        this.sessionLifetime = value;
    }

    public RsaPadding getRsaPadding() {
        return rsaPadding;
    }

    public void setRsaPadding(RsaPadding value) {
        this.rsaPadding = value;
    }

    public String getSessionPath() {
        return sessionPath;
    }

    public void setSessionPath(String value) {
        this.sessionPath = value;
    }

    public String getC2bPath() {
        return c2bPath;
    }

    public void setC2bPath(String value) {
        this.c2bPath = value;
    }

    public String getB2cPath() {
        return b2cPath;
    }

    public void setB2cPath(String value) {
        this.b2cPath = value;
    }

    public String getB2bPath() {
        return b2bPath;
    }

    public void setB2bPath(String value) {
        this.b2bPath = value;
    }

    public String getReversalPath() {
        return reversalPath;
    }

    public void setReversalPath(String value) {
        this.reversalPath = value;
    }

    public String getQueryStatusPath() {
        return queryStatusPath;
    }

    public void setQueryStatusPath(String value) {
        this.queryStatusPath = value;
    }

    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    public void setQueryMethod(QueryMethod value) {
        this.queryMethod = value;
    }

    public String effectiveBaseUrl() {
        String url = baseUrl != null ? baseUrl : production
                ? "https://openapi.m-pesa.com/openapi/ipg/v2/vodacomTZN/"
                : "https://openapi.m-pesa.com/sandbox/ipg/v2/vodacomTZN/";
        return url.endsWith("/") ? url : url + "/";
    }

    public void validate() {
        required(apiKey, "apiKey");
        required(publicKey, "publicKey");
        required(origin, "origin");
        required(serviceProviderCode, "serviceProviderCode");
        if (origin.contains("\r") || origin.contains("\n")) throw new IllegalArgumentException("Invalid origin");
        URI base = URI.create(effectiveBaseUrl());
        boolean local = "localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost()) || "[::1]".equals(base.getHost());
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !("https".equals(base.getScheme()) || (!production && local && "http".equals(base.getScheme()))))
            throw new IllegalArgumentException("baseUrl must be HTTPS (HTTP loopback is allowed in sandbox for tests)");
        for (String path : new String[]{sessionPath, c2bPath, b2cPath, b2bPath, reversalPath, queryStatusPath}) {
            required(path, "endpoint path");
            if (!path.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/?"))
                throw new IllegalArgumentException("Endpoint paths must be relative, without query or traversal");
        }
        positive(connectTimeout, "connectTimeout");
        positive(requestTimeout, "requestTimeout");
        positive(sessionLifetime, "sessionLifetime");
        Objects.requireNonNull(rsaPadding, "rsaPadding");
        Objects.requireNonNull(queryMethod, "queryMethod");
    }

    public enum RsaPadding {OAEP_SHA256, OAEP_SHA1, PKCS1}

    public enum QueryMethod {GET, POST}
}
