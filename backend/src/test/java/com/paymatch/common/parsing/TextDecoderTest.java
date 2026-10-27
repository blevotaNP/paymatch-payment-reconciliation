package com.paymatch.common.parsing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextDecoderTest {

    private static final String POLISH = "Zażółć gęślą jaźń; Łódź";

    @Test
    void decodesWindows1250() {
        byte[] bytes = POLISH.getBytes(TextDecoder.WINDOWS_1250);

        TextDecoder.DecodedText decoded = TextDecoder.decode(bytes);

        assertThat(decoded.text()).isEqualTo(POLISH);
        assertThat(decoded.charset()).isEqualTo(TextDecoder.WINDOWS_1250);
    }

    @Test
    void decodesUtf8WithoutBom() {
        TextDecoder.DecodedText decoded = TextDecoder.decode(POLISH.getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.text()).isEqualTo(POLISH);
        assertThat(decoded.charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void stripsUtf8Bom() {
        byte[] text = POLISH.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[text.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withBom, 3, text.length);

        TextDecoder.DecodedText decoded = TextDecoder.decode(withBom);

        assertThat(decoded.text()).isEqualTo(POLISH);
        assertThat(decoded.text().charAt(0)).isEqualTo('Z');
    }
}
