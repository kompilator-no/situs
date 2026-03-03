package no.testframework.javalibrary.api.http;

import java.util.LinkedHashMap;
import java.util.Map;

public record HttpResponseData(
        int statusCode,
        String body,
        Map<String, String> headers
) {
    public HttpResponseData {
        body = body == null ? "" : body;
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
    }
}
