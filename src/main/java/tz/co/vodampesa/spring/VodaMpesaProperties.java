package tz.co.vodampesa.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import tz.co.vodampesa.VodaMpesaConfig;

/**
 * One account, bound under vodampesa. Authentication is lazy unless auto-initialize is enabled.
 * @author Christopher oigo
 */
@ConfigurationProperties(prefix = "vodampesa")
public class VodaMpesaProperties extends VodaMpesaConfig {
    private boolean enabled = true;
    private boolean autoInitialize;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoInitialize() {
        return autoInitialize;
    }

    public void setAutoInitialize(boolean autoInitialize) {
        this.autoInitialize = autoInitialize;
    }
}
