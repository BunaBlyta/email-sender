package com.example.emailsender;

import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
public class EmailSenderApplication {

    private final UserRepository userRepository;
    private final Environment env;

    public EmailSenderApplication(UserRepository userRepository, Environment env) {
        this.userRepository = userRepository;
        this.env = env;
    }

    public static void main(String[] args) {
        SpringApplication.run(EmailSenderApplication.class, args);
    }

    // Seed a demo user on first run
    @Bean
    CommandLineRunner seedData() {
        return args -> {
            if (userRepository.count() == 0) {
                User user = new User();
                user.setEmail("test@example.com");
                user.setDisplayName("Demo User");
                userRepository.save(user);
                System.out.println("Demo user created.");
            }
        };
    }

    @Bean
    CommandLineRunner debugOAuthConfig() {
        return args -> {
            System.out.println("CLIENT ID: " + env.getProperty("GOOGLE_CLIENT_ID"));
            System.out.println("CLIENT SECRET present: " + (env.getProperty("GOOGLE_CLIENT_SECRET") != null));
        };
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("email-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
