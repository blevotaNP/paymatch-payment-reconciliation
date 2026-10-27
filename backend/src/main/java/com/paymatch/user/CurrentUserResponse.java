package com.paymatch.user;

public record CurrentUserResponse(String username, String displayName) {

    static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(user.getUsername(), user.getDisplayName());
    }
}
