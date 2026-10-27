package com.paymatch.user;

import com.paymatch.common.error.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final AppUserRepository userRepository;

    public CurrentUser(AppUserRepository userRepository) {
        this.userRepository = userRepository;
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
