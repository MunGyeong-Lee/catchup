package com.moon.catchup.service.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 첨부 파일의 실제 형식과 파일별 및 요청별 크기를 검사한다. */
@Component
public class SourceAttachmentValidator {
    private static final long FILE_LIMIT = 20L * 1024 * 1024;
    private static final long REQUEST_LIMIT = 100L * 1024 * 1024;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    /** 검증된 첨부의 원본, 파일 이름, 감지한 콘텐츠 유형을 보관한다. */
    @Getter
    @AllArgsConstructor
    public static final class File {
        private final MultipartFile content;
        private final String name;
        private final String type;
    }

    /** 첨부 파일 또는 요청 내 첨부 합계가 크기 상한을 넘었음을 나타낸다. */
    public static final class SizeLimitExceededException extends IllegalArgumentException {}

    /**
     * 유효한 첨부의 안전한 이름과 확인된 콘텐츠 유형을 반환한다.
     *
     * @param files 요청에 포함된 첨부파일 목록
     * @return 요청 순서대로 정리한 유효한 첨부파일 목록
     */
    public List<File> validate(List<MultipartFile> files) {
        long total = 0;
        List<File> validated = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            String original = file.getOriginalFilename();
            String name = original == null ? "" : original.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
            if (name.isBlank() || name.equals(".") || name.equals("..")) name = "attachment";
            long size = file.getSize();
            if (size <= 0) throw new IllegalArgumentException(name + ": 빈 파일은 첨부할 수 없습니다.");
            if (size > FILE_LIMIT) throw new SizeLimitExceededException();
            total += size;
            if (total > REQUEST_LIMIT) {
                throw new SizeLimitExceededException();
            }
            try (InputStream input = file.getInputStream()) {
                String type = detect(input.readNBytes(12));
                if (type == null) throw new IllegalArgumentException(name + ": 지원하지 않는 파일 형식입니다.");
                validated.add(new File(file, name, type));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return List.copyOf(validated);
    }

    /**
     * 파일의 시작 바이트를 읽어 지원하는 콘텐츠 유형을 판별한다.
     *
     * @param bytes 파일 형식을 확인할 시작 바이트
     * @return 감지한 콘텐츠 유형 또는 일치하는 형식이 없으면 null
     */
    private static String detect(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff) return "image/jpeg";
        if (bytes.length >= 8 && Arrays.equals(bytes, 0, 8, PNG, 0, 8)) return "image/png";
        if (bytes.length >= 12 && magic(bytes, 0, "RIFF") && magic(bytes, 8, "WEBP")) return "image/webp";
        if (bytes.length >= 8 && magic(bytes, 0, "%PDF-")
                && bytes[5] >= '1' && bytes[5] <= '2'
                && bytes[6] == '.' && bytes[7] >= '0' && bytes[7] <= '9') return "application/pdf";
        return null;
    }

    /**
     * 지정 위치의 바이트가 예상 파일 서명과 일치하는지 확인한다.
     *
     * @param bytes 파일 형식을 확인할 시작 바이트
     * @param offset 시그니처 비교를 시작할 바이트 위치
     * @param signature 비교할 파일 시그니처
     * @return 해당 위치의 바이트가 시그니처와 일치하면 true
     */
    private static boolean magic(byte[] bytes, int offset, String signature) {
        for (int i = 0; i < signature.length(); i++) {
            if (bytes[offset + i] != signature.charAt(i)) return false;
        }
        return true;
    }
}
