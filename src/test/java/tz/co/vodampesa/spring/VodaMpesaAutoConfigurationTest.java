package tz.co.vodampesa.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tz.co.vodampesa.VodaMpesaConfig;
import tz.co.vodampesa.VodaMpesaSdk;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class VodaMpesaAutoConfigurationTest {
    static String publicKey;

    @BeforeAll
    static void keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        publicKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    }

    ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(VodaMpesaAutoConfiguration.class));
    }

    ApplicationContextRunner configured() {
        return runner().withPropertyValues("vodampesa.api-key=test-key", "vodampesa.public-key=" + publicKey,
                "vodampesa.origin=merchant.example", "vodampesa.service-provider-code=000000");
    }

    @Test
    void createsSingleLazySdkAndBindsInheritedProperties() {
        configured().withPropertyValues("vodampesa.request-timeout=12s", "vodampesa.query-method=GET", "vodampesa.rsa-padding=PKCS1")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VodaMpesaSdk.class);
                    assertThat(context.getBean(VodaMpesaSdk.class).isInitialized()).isFalse();
                    VodaMpesaProperties properties = context.getBean(VodaMpesaProperties.class);
                    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(12));
                    assertThat(properties.getServiceProviderCode()).isEqualTo("000000");
                    assertThat(properties.getQueryMethod()).isEqualTo(VodaMpesaConfig.QueryMethod.GET);
                    assertThat(properties.getRsaPadding()).isEqualTo(VodaMpesaConfig.RsaPadding.PKCS1);
                });
    }

    @Test
    void disabledIntegrationRequiresNoCredentials() {
        runner().withPropertyValues("vodampesa.enabled=false").run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(VodaMpesaSdk.class));
    }

    @Test
    void missingCredentialsFailEarly() {
        runner().run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationProvidedSdkTakesPrecedence() {
        VodaMpesaConfig config = new VodaMpesaConfig();
        config.setApiKey("key");
        config.setPublicKey(publicKey);
        config.setOrigin("merchant.example");
        config.setServiceProviderCode("000000");
        VodaMpesaSdk custom = new VodaMpesaSdk(config);
        runner().withBean(VodaMpesaSdk.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(VodaMpesaSdk.class);
            assertThat(context.getBean(VodaMpesaSdk.class)).isSameAs(custom);
        });
    }

    @Test
    void discoversAutoConfigurationFromPackagedImports() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).contains(VodaMpesaAutoConfiguration.class.getName());
        }
    }
}
