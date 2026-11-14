package com.aaditi.travelplanner.config;
import com.aaditi.travelplanner.model.User;
import com.aaditi.travelplanner.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
 @Bean CommandLineRunner seed(UserRepository repo, PasswordEncoder encoder){ return args -> {
   if(repo.findByEmailIgnoreCase("admin@travelai.com").isEmpty()){
     User u=new User(); u.setName("TravelAI Administrator"); u.setEmail("admin@travelai.com"); u.setPasswordHash(encoder.encode("Admin@123")); u.setRole("ADMIN"); u.setCity("India"); repo.save(u);
   }
 };}
}
