package com.example.emailsender.tracking;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TrackingPixelService {

    private static final String BADGE_PATH = "static/images/email-platform-badge.png";

    private final byte[] badge;

    public TrackingPixelService() {
        this.badge = loadBadge();
    }

    public byte[] badge() {
        return badge.clone();
    }

    private byte[] loadBadge() {
        try {
            return new ClassPathResource(BADGE_PATH).getContentAsByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load tracking badge", exception);
        }
    }
}
