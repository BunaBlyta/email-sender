package com.example.emailsender.auth;

import com.example.emailsender.user.ConnectedAccount;
import com.example.emailsender.user.ConnectedAccountRepository;
import com.example.emailsender.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class TokenStore {

    private final ConnectedAccountRepository connectedAccountRepository;
    private final Environment env;

    public TokenStore(ConnectedAccountRepository connectedAccountRepository, Environment env) {
        this.connectedAccountRepository = connectedAccountRepository;
        this.env = env;
    }

    public String getValidAccessToken(User user) {
        List<ConnectedAccount> accounts = connectedAccountRepository.findByUser(user);
        ConnectedAccount account = accounts.stream()
                .filter(a -> a.getProvider() == User.Provider.GMAIL)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No Gmail account connected for user: " + user.getEmail()));

        if (account.getTokenExpiry() != null
                && account.getTokenExpiry().isBefore(LocalDateTime.now(java.time.Clock.systemUTC()))) {
            if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
                throw new RuntimeException("Gmail authorization expired; reconnect the account");
            }
            String newAccessToken = refreshAccessToken(account.getRefreshToken());
            account.setAccessToken(newAccessToken);
            account.setTokenExpiry(LocalDateTime.now(java.time.Clock.systemUTC()).plusHours(1));
            connectedAccountRepository.save(account);
        }

        return account.getAccessToken();
    }

    public String refreshAccessToken(String refreshToken) {
        String clientId = env.getProperty("GOOGLE_CLIENT_ID");
        String clientSecret = env.getProperty("GOOGLE_CLIENT_SECRET");
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("Google OAuth client credentials are not configured");
        }

        String body = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            if (response.statusCode() < 200 || response.statusCode() >= 300 || root.has("error")) {
                String error = root.has("error")
                        ? root.get("error").asText()
                        : "HTTP " + response.statusCode();
                throw new RuntimeException("Token refresh failed: " + error);
            }
            return root.get("access_token").asText();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to refresh token", e);
        }
    }
}
