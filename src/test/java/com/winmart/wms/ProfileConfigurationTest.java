package com.winmart.wms;

import com.winmart.wms.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProfileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                context.getEnvironment().getPropertySources().remove("systemEnvironment");
                context.getEnvironment().getPropertySources().remove("systemProperties");
                new ConfigDataApplicationContextInitializer().initialize(context);
            })
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(JwtTokenProvider.class);

    @Test
    void defaultProfileRequiresJwtSecret() {
        contextRunner.run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("Could not resolve placeholder 'JWT_SECRET' in value \"${JWT_SECRET}\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "too-short"})
    void deploymentRejectsEmptyOrShortJwtSecret(String secret) {
        contextRunner.withPropertyValues("JWT_SECRET=" + secret).run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void deploymentHasNoDatabaseCredentialFallbacksOrCorsOrigins() {
        contextRunner.withPropertyValues("JWT_SECRET=" + "x".repeat(32)).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getDefaultProfiles()).containsExactly("mysql");
            assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("app.security.cors.allowed-origins")).isEmpty();
            for (String property : new String[]{"url", "username", "password"}) {
                assertThatIllegalArgumentException().isThrownBy(() ->
                        environment.getRequiredProperty("spring.datasource." + property));
            }
        });
    }

    @Test
    void devStartsWithoutExternalSecretsAndEnablesH2() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("app.jwt.secret")).hasSizeGreaterThanOrEqualTo(32);
            assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
            assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isTrue();
        });
    }
}
