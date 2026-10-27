package com.paymatch.statement;

import com.paymatch.common.parsing.TextDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class TestFiles {

    private TestFiles() {
    }

    public static byte[] bytes(String classpathLocation) {
        try (InputStream in = TestFiles.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalArgumentException("Test file not found: " + classpathLocation);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Decoded the same way the import service does it. */
    public static String text(String classpathLocation) {
        return TextDecoder.decode(bytes(classpathLocation)).text();
    }

    public static String sample(String fileName) {
        return text("samples/statements/" + fileName);
    }

    public static String broken(String fileName) {
        return text("statements/broken/" + fileName);
    }
}
