package no.testframework.javalibrary.suite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public record HttpEndpoint(
        String path,
        Map<String, String> headers,
        Supplier<HttpBody> bodySupplier
) {
    public HttpEndpoint {
        Objects.requireNonNull(path, "path cannot be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        bodySupplier = Objects.requireNonNull(bodySupplier, "bodySupplier cannot be null");
    }

    public static HttpEndpoint from(String path, Supplier<HttpBody> bodySupplier) {
        return new HttpEndpoint(path, Map.of(), bodySupplier);
    }

    public static HttpEndpoint from(String path, Map<String, String> headers, Supplier<HttpBody> bodySupplier) {
        return new HttpEndpoint(path, headers, bodySupplier);
    }
}
