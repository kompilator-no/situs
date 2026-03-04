package no.testframework.javalibrary.suite;

import java.util.List;
import java.util.Objects;

public record HttpEndpoints(
        int port,
        List<HttpEndpoint> endpoints
) {
    public HttpEndpoints {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints cannot be null"));
    }

    public static HttpEndpoints from(int port, List<HttpEndpoint> endpoints) {
        return new HttpEndpoints(port, endpoints);
    }
}
