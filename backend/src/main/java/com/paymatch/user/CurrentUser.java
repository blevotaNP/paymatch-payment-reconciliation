package com.paymatch.user;

import com.paymatch.common.error.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUser {

    private final AppUserRepository userRepository;

    public CurrentUser(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Empty when nobody is logged in (e.g. startup tasks). */
    public Optional<AppUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName());
    }

    public AppUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User " + authentication.getName() + " not found"));
    }
}
