package tz.co.vodampesa.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tz.co.vodampesa.VodaMpesaConfig;
import tz.co.vodampesa.VodaMpesaSdk;

/**
 * Optional Spring Boot 3 integration for a single M-Pesa account.
 * @author Christopher oigo
 */
@AutoConfiguration
@EnableConfigurationProperties(VodaMpesaProperties.class)
@ConditionalOnProperty(prefix = "vodampesa", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VodaMpesaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(VodaMpesaSdk.class)
    public VodaMpesaSdk vodaMpesaSdk(VodaMpesaProperties properties) {
        VodaMpesaSdk sdk = new VodaMpesaSdk(new VodaMpesaConfig(properties));
        if (properties.isAutoInitialize()) sdk.initialize();
        return sdk;
    }
}
