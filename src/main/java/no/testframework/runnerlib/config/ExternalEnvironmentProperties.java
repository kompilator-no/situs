package no.testframework.runnerlib.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration")
public class ExternalEnvironmentProperties {

    private Kafka kafka = new Kafka();
    private Services services = new Services();
    private Auth auth = new Auth();

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Services getServices() {
        return services;
    }

    public void setServices(Services services) {
        this.services = services;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class Kafka {
        private List<String> brokers = new ArrayList<>();

        public List<String> getBrokers() {
            return brokers;
        }

        public void setBrokers(List<String> brokers) {
            this.brokers = brokers;
        }
    }

    public static class Services {
        private String orchestratorBaseUrl;
        private String testDataBaseUrl;

        public String getOrchestratorBaseUrl() {
            return orchestratorBaseUrl;
        }

        public void setOrchestratorBaseUrl(String orchestratorBaseUrl) {
            this.orchestratorBaseUrl = orchestratorBaseUrl;
        }

        public String getTestDataBaseUrl() {
            return testDataBaseUrl;
        }

        public void setTestDataBaseUrl(String testDataBaseUrl) {
            this.testDataBaseUrl = testDataBaseUrl;
        }
    }

    public static class Auth {
        private String clientId;
        private String clientSecret;
        private String tokenUrl;

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

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }
    }
}
