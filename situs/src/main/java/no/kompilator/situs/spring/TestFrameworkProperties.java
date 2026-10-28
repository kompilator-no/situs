package no.kompilator.situs.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * External configuration for the Situs Spring integration layer.
 *
 * <p>Discovery defaults to package-scoped scanning in Spring Boot applications by
 * using the application's auto-configuration base packages. Full classpath scanning
 * is retained as an explicit opt-in and as a fallback for non-Boot Spring usage.
 */
@ConfigurationProperties(prefix = "testframework")
public class TestFrameworkProperties {

    /**
     * Explicit packages to scan for {@code @TestSuite} classes.
     *
     * <p>When empty, Spring Boot applications fall back to their auto-configuration
     * base packages. Non-Boot Spring applications fall back to a full classpath scan.
     */
    private List<String> scanPackages = new ArrayList<>();

    /**
     * Enables full classpath scanning explicitly.
     *
     * <p>Prefer package-scoped scanning whenever possible because it is cheaper and
     * more predictable in real applications.
     */
    private boolean fullClasspathScan = false;

    /**
     * Maximum number of completed/failed run snapshots kept, in memory or on disk.
     */
    private int maxStoredRuns = 200;

    /**
     * Bearer token required on the HTTP API, sent as {@code Authorization: Bearer <token>}.
     *
     * <p>When unset or blank the API is open and a warning is logged at startup.
     * The {@code /status} health check never requires the token.
     */
    private String apiToken;

    /**
     * Directory where finished run results are written, one file per run. Results written here
     * survive application restarts.
     *
     * <p>When unset, finished runs are kept in memory only and are lost on restart.
     */
    private String resultDirectory;

    public List<String> getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(List<String> scanPackages) {
        this.scanPackages = scanPackages == null ? new ArrayList<>() : new ArrayList<>(scanPackages);
    }

    public boolean isFullClasspathScan() {
        return fullClasspathScan;
    }

    public void setFullClasspathScan(boolean fullClasspathScan) {
        this.fullClasspathScan = fullClasspathScan;
    }

    public int getMaxStoredRuns() {
        return maxStoredRuns;
    }

    public void setMaxStoredRuns(int maxStoredRuns) {
        this.maxStoredRuns = maxStoredRuns;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getResultDirectory() {
        return resultDirectory;
    }

    public void setResultDirectory(String resultDirectory) {
        this.resultDirectory = resultDirectory;
    }
}
