package com.paymatch.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates two demo accounts on an empty database so the app can be tried right after start. */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoUsersProperties properties;

    public DemoUserSeeder(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                          DemoUsersProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || userRepository.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(properties.password());
        userRepository.save(new AppUser("anna", "Anna Kowalska", hash));
        userRepository.save(new AppUser("piotr", "Piotr Nowak", hash));
        log.info("Created demo users 'anna' and 'piotr'");
    }
}
