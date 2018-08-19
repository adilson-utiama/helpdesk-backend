package com.asuprojects.helpdesk;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.asuprojects.helpdesk.api.entity.User;
import com.asuprojects.helpdesk.api.enums.ProfileEnum;
import com.asuprojects.helpdesk.api.repository.UserRepository;

@SpringBootApplication
public class HelpDeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelpDeskApplication.class, args);
	}
	
	@Bean
	CommandLineRunner init(UserRepository userRepository, PasswordEncoder encoder) {
		return args -> {
			initUsers(userRepository, encoder);
		};
	}

	private void initUsers(UserRepository userRepository, PasswordEncoder encoder) {
		User admin = new User();
		admin.setEmail("adilson@gmail.com");
		admin.setPassword(encoder.encode("123456"));
		admin.setProfile(ProfileEnum.ROLE_ADMIN);
		
		User find = userRepository.findByEmail("adilson@gmail.com");
		if(find == null) {
			userRepository.save(admin);
		}
	
	}
}
