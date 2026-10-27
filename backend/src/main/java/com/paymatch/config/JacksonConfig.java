package com.paymatch.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

@Configuration
public class JacksonConfig {

    /** Amounts go out as strings ("1230.50") so that no client ever parses them into a float. */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer bigDecimalAsString() {
        return builder -> builder.serializerByType(BigDecimal.class, new BigDecimalStringSerializer());
    }

    static class BigDecimalStringSerializer extends StdSerializer<BigDecimal> {

        BigDecimalStringSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toPlainString());
        }
    }
}
