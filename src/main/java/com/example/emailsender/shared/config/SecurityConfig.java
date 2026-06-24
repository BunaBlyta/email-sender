package com.example.emailsender.shared.config;

import com.example.emailsender.auth.OAuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuthService oauthService;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository,
                          OAuth2AuthorizedClientService authorizedClientService,
                          OAuthService oauthService) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizedClientService = authorizedClientService;
        this.oauthService = oauthService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/inbox/**",
                                "/send/**",
                                "/templates/**",
                                "/scheduled/**",
                                "/recipient-groups/**",
                                "/tracking/**",
                                "/security/**"
                        ).authenticated()
                        .requestMatchers("/track/**").permitAll()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler((request, response, authentication) -> {
                            OAuth2AuthenticationToken oauthToken =
                                    (OAuth2AuthenticationToken) authentication;
                            OAuth2User principal = oauthToken.getPrincipal();

                            OAuth2AuthorizedClient authorizedClient = authorizedClientService
                                    .loadAuthorizedClient(
                                            oauthToken.getAuthorizedClientRegistrationId(),
                                            oauthToken.getName()
                            );

                            oauthService.handleOAuthSuccess(principal, authorizedClient);
                            response.sendRedirect("/inbox/threads");
                        })
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
                    params.put("access_type", "offline");
                    params.put("prompt", "consent");
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
