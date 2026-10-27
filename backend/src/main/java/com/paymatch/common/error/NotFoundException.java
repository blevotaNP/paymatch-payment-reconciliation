package com.paymatch.common.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Long id) {
        return new NotFoundException(entity + " with id " + id + " not found");
    }
}
