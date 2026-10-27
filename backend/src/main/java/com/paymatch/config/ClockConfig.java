package com.paymatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** "Today" for due dates and overdue checks is the business day in Poland, not the server's zone. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Warsaw");

    @Bean
    Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
