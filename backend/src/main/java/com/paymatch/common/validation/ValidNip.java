package com.paymatch.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Null or blank values are considered valid, combine with @NotBlank when the field is required. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NipValidator.class)
public @interface ValidNip {

    String message() default "invalid NIP";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
