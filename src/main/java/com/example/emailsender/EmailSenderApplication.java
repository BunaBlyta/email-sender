package com.example.emailsender;

import com.example.emailsender.model.Draft;
import com.example.emailsender.model.User;
import com.example.emailsender.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
public class EmailSenderApplication {

	private final UserRepository userRepository;

	public EmailSenderApplication(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public static void main(String[] args) {
		SpringApplication.run(EmailSenderApplication.class, args);
	}

	// Seed demo user + draft ONLY – no email sending
	@Bean
	CommandLineRunner seedData() {
		return args -> {
			if (userRepository.count() == 0) {
				User user = new User("test@example.com", "password");
				userRepository.save(user);

				Draft draft = new Draft("bunablyta@gmail.com",
						"Hello from Spring Boot",
						"This is a test email body.",
						user);

				System.out.println("Demo user and draft created.");
			}
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
