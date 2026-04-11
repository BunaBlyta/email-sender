package com.example.emailsender.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // TODO: Configure thread pool executor for async mail sending and background sync jobs
}
