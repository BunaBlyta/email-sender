package com.example.emailsender.auth;

import com.example.emailsender.user.ConnectedAccount;
import com.example.emailsender.user.ConnectedAccountRepository;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OAuthService {

    private final UserRepository userRepository;
    private final ConnectedAccountRepository connectedAccountRepository;

    public OAuthService(UserRepository userRepository,
                        ConnectedAccountRepository connectedAccountRepository) {
        this.userRepository = userRepository;
        this.connectedAccountRepository = connectedAccountRepository;
    }

    public User handleOAuthSuccess(OAuth2User principal, OAuth2AuthorizedClient authorizedClient) {
        String email = principal.getAttribute("email");
        String displayName = principal.getAttribute("name");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setDisplayName(displayName);
            return userRepository.save(newUser);
        });

        List<ConnectedAccount> accounts = connectedAccountRepository.findByUser(user);
        ConnectedAccount account = accounts.stream()
                .filter(a -> email.equals(a.getAccountEmail()))
                .findFirst()
                .orElse(null);

        boolean isNew = account == null;
        if (isNew) {
            account = new ConnectedAccount();
            account.setUser(user);
            account.setConnectedAt(LocalDateTime.now());
        }

        account.setProvider(User.Provider.GMAIL);
        account.setAccountEmail(email);
        account.setAccessToken(authorizedClient.getAccessToken().getTokenValue());

        if (authorizedClient.getRefreshToken() != null) {
            account.setRefreshToken(authorizedClient.getRefreshToken().getTokenValue());
        }

        Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
        account.setTokenExpiry(expiresAt != null
                ? LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
                : null);

        connectedAccountRepository.save(account);

        return user;
    }
}
