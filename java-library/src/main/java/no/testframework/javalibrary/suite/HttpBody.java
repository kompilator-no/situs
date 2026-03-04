package no.testframework.javalibrary.suite;

public record HttpBody(String value) {
    public HttpBody {
        value = value == null ? "" : value;
    }
}
