package com.example.emailsender.user;

public record CurrentUserResponse(
        String email,
        String name,
        String picture
) {
}
