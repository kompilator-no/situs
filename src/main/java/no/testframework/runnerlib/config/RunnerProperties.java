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
}
