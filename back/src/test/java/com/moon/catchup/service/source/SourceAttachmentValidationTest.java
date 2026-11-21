package com.moon.catchup.service.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 첨부 파일 형식과 크기 검증의 경계 조건을 확인한다. */
public class SourceAttachmentValidationTest {
    private final SourceAttachmentValidator validator = new SourceAttachmentValidator();

    /** 실제 바이트로 형식을 판별하고 작은 파일 여러 개를 허용하는지 검증한다. */
    @Test
    public void identifiesSupportedFormatsFromBytesAndAllowsManyFiles() {
        assertEquals("image/jpeg", validator.validate(List.of(file(0xff, 0xd8, 0xff))).getFirst().getType());
        assertEquals("image/png", validator.validate(List.of(file(137, 80, 78, 71, 13, 10, 26, 10))).getFirst().getType());
        assertEquals("image/webp", validator.validate(List.of(file(82, 73, 70, 70, 4, 0, 0, 0, 87, 69, 66, 80))).getFirst().getType());
        assertEquals(200, validator.validate(Collections.nCopies(200, pdf())).size());
    }

    /** 허용 확장자로 위장한 파일을 거부하는지 검증한다. */
    @Test
    public void rejectsDisguisedFiles() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(file(60, 104, 116, 109, 108))));
    }

    /** 파일당과 한 요청의 첨부 합계 크기 경계를 검증한다. */
    @Test
    public void appliesExactByteLimits() throws Exception {
        MockMultipartFile file = spy(pdf());
        when(file.getSize()).thenReturn(20L * 1024 * 1024);
        assertEquals(5, validator.validate(Collections.nCopies(5, file)).size());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(Collections.nCopies(6, file)));
        when(file.getSize()).thenReturn(20L * 1024 * 1024 + 1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(file)));
    }

    /** 파일 형식 검증에 사용할 피디에프 표본을 만든다. */
    public static MockMultipartFile pdf() {
        return new MockMultipartFile("files", "sample.pdf", "application/octet-stream", "%PDF-1.7\n".getBytes());
    }

    /** 지정한 바이트 서명으로 형식 검증용 파일을 만든다. */
    private static MockMultipartFile file(int... signature) {
        byte[] bytes = new byte[signature.length];
        for (int i = 0; i < signature.length; i++) bytes[i] = (byte) signature[i];
        return new MockMultipartFile("files", "example.pdf", "application/pdf", bytes);
    }
}
