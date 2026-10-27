package com.paymatch.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-users")
public record DemoUsersProperties(boolean enabled, String password) {
}
