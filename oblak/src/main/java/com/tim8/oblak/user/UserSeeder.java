package com.tim8.oblak.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeduje pocetne korisnike u praznu bazu.
 * Korisno za H2 in-memory bazu koja se brise pri svakom restartu.
 * U produkciji bi trebalo da koristi pravu registraciju ili migracioni alat.
 */
@Configuration
public class UserSeeder {

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin"));
                admin.setRole(User.Role.ADMIN);
                userRepository.save(admin);
            }
            if (!userRepository.existsByUsername("user")) {
                User user = new User();
                user.setUsername("user");
                user.setPassword(passwordEncoder.encode("user"));
                user.setRole(User.Role.USER);
                userRepository.save(user);
            }
        };
    }
}
