package no.testframework.runnerlib.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "runner")
public class RunnerProperties {

    private List<String> discoveryPackages = List.of("no.testframework.sampleapp");

    @Min(1)
    private int concurrency = 4;

    @Min(1)
    private int queueCapacity = 100;

    private Duration defaultTimeout = Duration.ofMinutes(5);

    @Min(0)
    private int defaultRetries = 1;

    private Security security = new Security();

    public List<String> getDiscoveryPackages() {
        return discoveryPackages;
    }

    public void setDiscoveryPackages(List<String> discoveryPackages) {
        this.discoveryPackages = discoveryPackages;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public int getDefaultRetries() {
        return defaultRetries;
    }

    public void setDefaultRetries(int defaultRetries) {
        this.defaultRetries = defaultRetries;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Security {
        private Jwt jwt = new Jwt();
        private Opaque opaque = new Opaque();

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public Opaque getOpaque() {
            return opaque;
        }

        public void setOpaque(Opaque opaque) {
            this.opaque = opaque;
        }

        public static class Jwt {
            private String issuerUri;
            private String audience;

            public String getIssuerUri() {
                return issuerUri;
            }

            public void setIssuerUri(String issuerUri) {
                this.issuerUri = issuerUri;
            }

            public String getAudience() {
                return audience;
            }

            public void setAudience(String audience) {
                this.audience = audience;
            }
        }

        public static class Opaque {
            private boolean enabled = false;
            private String introspectionUri;
            private String clientId;
            private String clientSecret;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getIntrospectionUri() {
                return introspectionUri;
            }

            public void setIntrospectionUri(String introspectionUri) {
                this.introspectionUri = introspectionUri;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String clientId) {
                this.clientId = clientId;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
            }
        }
    }
}
