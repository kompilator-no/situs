package no.testframework.sampleapp;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class ObservabilityConfig {

    @Bean
    OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    @Bean
    CommandLineRunner startupTracing(OpenTelemetry openTelemetry) {
        return args -> {
            Span startup = openTelemetry.getTracer("sample-app").spanBuilder("sample-app.startup").startSpan();
            startup.setAttribute("app.name", "sample-runner");
            startup.end();
        };
    }

    @Bean
    OncePerRequestFilter httpTracingFilter(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer("sample-app-http");
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String correlationId = request.getHeader("X-Correlation-Id");
                if (correlationId == null || correlationId.isBlank()) {
                    correlationId = UUID.randomUUID().toString();
                }
                response.setHeader("X-Correlation-Id", correlationId);

                Span span = tracer.spanBuilder(request.getMethod() + " " + request.getRequestURI()).startSpan();
                span.setAttribute("correlation.id", correlationId);
                span.setAttribute("http.route", request.getRequestURI());
                try (Scope ignored = span.makeCurrent()) {
                    filterChain.doFilter(request, response);
                    span.setAttribute("http.status_code", response.getStatus());
                } catch (Exception e) {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR);
                    throw e;
                } finally {
                    span.end();
                }
            }
        };
    }
}
