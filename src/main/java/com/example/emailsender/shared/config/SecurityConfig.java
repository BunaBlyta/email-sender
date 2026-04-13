package com.example.emailsender.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("/inbox", true)
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(noPkceResolver())
                        )
                        .tokenEndpoint(token -> token
                                .accessTokenResponseClient(noPkceTokenClient())
                        )
                )
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    private OAuth2AuthorizationRequestResolver noPkceResolver() {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.additionalParameters(params -> {
                    params.remove("code_challenge");
                    params.remove("code_challenge_method");
                })
        );
        return resolver;
    }

    private org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> noPkceTokenClient() {
        RestClientAuthorizationCodeTokenResponseClient delegate =
                new RestClientAuthorizationCodeTokenResponseClient();

        return grantRequest -> {
            OAuth2AuthorizationRequest authRequest = grantRequest
                    .getAuthorizationExchange()
                    .getAuthorizationRequest();
            OAuth2AuthorizationResponse authResponse = grantRequest
                    .getAuthorizationExchange()
                    .getAuthorizationResponse();

            OAuth2AuthorizationRequest cleanRequest = OAuth2AuthorizationRequest
                    .from(authRequest)
                    .attributes(attrs -> attrs.remove("code_verifier"))
                    .build();

            OAuth2AuthorizationCodeGrantRequest cleanGrantRequest =
                    new OAuth2AuthorizationCodeGrantRequest(
                            grantRequest.getClientRegistration(),
                            new OAuth2AuthorizationExchange(cleanRequest, authResponse)
                    );

            return delegate.getTokenResponse(cleanGrantRequest);
        };
    }
}