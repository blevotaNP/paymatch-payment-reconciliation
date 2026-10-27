package com.paymatch.common.parsing;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodes uploaded files. Polish banks still export a lot of files in Windows-1250, others use UTF-8,
 * sometimes with a BOM. Strategy: BOM means UTF-8, otherwise try strict UTF-8 and fall back to Windows-1250.
 * Windows-1250 bytes for Polish letters (e.g. 0xB9 for "ą") are almost never valid UTF-8 sequences,
 * so the fallback is reliable in practice.
 */
public final class TextDecoder {

    public static final Charset WINDOWS_1250 = Charset.forName("windows-1250");

    private TextDecoder() {
    }

    public static DecodedText decode(byte[] bytes) {
        if (hasUtf8Bom(bytes)) {
            return new DecodedText(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new DecodedText(text, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            return new DecodedText(new String(bytes, WINDOWS_1250), WINDOWS_1250);
        }
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    public record DecodedText(String text, Charset charset) {
    }
}
