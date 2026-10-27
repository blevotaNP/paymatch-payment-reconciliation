package com.paymatch.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param autoConfirmExact confirm perfect exact matches without review
 */
@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(boolean autoConfirmExact) {
}
