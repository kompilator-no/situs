package no.testframework.sampleapp;

import java.util.ArrayList;
import java.util.List;
import no.testframework.runnerlib.config.RunnerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            RunnerProperties runnerProperties,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            OpaqueTokenIntrospector opaqueTokenIntrospector) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> {
                if (runnerProperties.getSecurity().getOpaque().isEnabled()) {
                    oauth2.opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector));
                } else {
                    oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter));
                }
            });

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("scope");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(RunnerProperties runnerProperties) {
        RunnerProperties.Security.Jwt jwt = runnerProperties.getSecurity().getJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(jwt.getIssuerUri()).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()));
        if (jwt.getAudience() != null && !jwt.getAudience().isBlank()) {
            validators.add(token -> token.getAudience().contains(jwt.getAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The required audience is missing", null)));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    OpaqueTokenIntrospector opaqueTokenIntrospector(RunnerProperties runnerProperties) {
        RunnerProperties.Security.Opaque opaque = runnerProperties.getSecurity().getOpaque();
        return new NimbusOpaqueTokenIntrospector(
            opaque.getIntrospectionUri(),
            opaque.getClientId(),
            opaque.getClientSecret()
        );
    }
}
