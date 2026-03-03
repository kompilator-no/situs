package no.testframework.javalibrary.api.http;

import no.testframework.javalibrary.domain.TestAction;
import no.testframework.javalibrary.domain.TestValidator;
import no.testframework.javalibrary.runtime.TestExecutionContext;
import no.testframework.javalibrary.runtime.TestRuntimeConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HttpHandlers {
    public static final String ACTION_HTTP_REQUEST = "httpRequest";
    public static final String VALIDATOR_HTTP_STATUS_EQUALS = "httpStatusEquals";
    public static final String CONTEXT_LAST_HTTP_RESPONSE = "lastHttpResponse";

    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newHttpClient();

    private HttpHandlers() {
    }

    public static TestRuntimeConfiguration register(TestRuntimeConfiguration configuration) {
        return register(configuration, DEFAULT_HTTP_CLIENT);
    }

    public static TestRuntimeConfiguration register(TestRuntimeConfiguration configuration, HttpClient httpClient) {
        Objects.requireNonNull(configuration, "configuration cannot be null");
        HttpClient validHttpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");

        return configuration
                .registerActionHandler(ACTION_HTTP_REQUEST, (action, context) -> executeHttpAction(action, context, validHttpClient))
                .registerValidatorHandler(VALIDATOR_HTTP_STATUS_EQUALS, HttpHandlers::executeHttpStatusValidator);
    }

    private static void executeHttpAction(TestAction action, TestExecutionContext context, HttpClient httpClient) {
        String url = requiredStringParameter(action.parameters(), "url", ACTION_HTTP_REQUEST);
        String method = optionalStringParameter(action.parameters(), "method", "GET").toUpperCase();
        String body = optionalStringParameter(action.parameters(), "body", "");
        long timeoutMs = optionalNumberParameter(action.parameters(), "timeoutMs", 10_000L);
        String responseContextKey = optionalStringParameter(action.parameters(), "responseContextKey", CONTEXT_LAST_HTTP_RESPONSE);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs));

        Object headers = action.parameters().get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                requestBuilder.header(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        HttpRequest.BodyPublisher publisher = body.isBlank()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        HttpRequest request = requestBuilder.method(method, publisher).build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HttpResponseData responseData = new HttpResponseData(
                    response.statusCode(),
                    response.body(),
                    copyHeaders(response.headers().map())
            );
            context.put(responseContextKey, responseData);
            context.put(CONTEXT_LAST_HTTP_RESPONSE, responseData);
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", exception);
        }
    }

    private static void executeHttpStatusValidator(TestValidator validator, TestExecutionContext context) {
        String responseContextKey = optionalStringParameter(validator.expected(), "responseContextKey", CONTEXT_LAST_HTTP_RESPONSE);
        Object expectedStatus = validator.expected().get("status");
        if (!(expectedStatus instanceof Number typedStatus)) {
            throw new IllegalArgumentException("httpStatusEquals validator requires numeric 'status' expected value");
        }

        HttpResponseData responseData = context.get(responseContextKey, HttpResponseData.class);
        if (responseData == null) {
            throw new IllegalStateException("No HTTP response found in context key '%s'".formatted(responseContextKey));
        }

        if (responseData.statusCode() != typedStatus.intValue()) {
            throw new IllegalStateException("Expected HTTP status %d but was %d"
                    .formatted(typedStatus.intValue(), responseData.statusCode()));
        }
    }

    private static String requiredStringParameter(Map<String, Object> values, String key, String handlerType) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("%s action requires non-blank '%s' parameter".formatted(handlerType, key));
        }
        return text;
    }

    private static String optionalStringParameter(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private static long optionalNumberParameter(Map<String, Object> values, String key, long defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Parameter '%s' must be numeric".formatted(key));
    }

    private static Map<String, String> copyHeaders(Map<String, List<String>> source) {
        Map<String, String> headers = new LinkedHashMap<>();
        source.forEach((key, values) -> headers.put(key, String.join(",", values)));
        return Map.copyOf(headers);
    }
}
