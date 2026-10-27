package com.paymatch.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-data")
public record DemoDataProperties(boolean enabled, String directory) {
}
