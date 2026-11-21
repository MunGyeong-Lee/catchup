package com.moon.catchup.service.source;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceAttachmentSummaryVo;
import com.moon.catchup.domain.source.StoredAttachmentVo;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.storage.source.SourceAttachmentStorage;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소스에 연결된 첨부 목록과 원본을 열기 위한 정보를 제공한다. */
@Service
@RequiredArgsConstructor
public class SourceAttachmentServiceImpl implements SourceAttachmentService {
    private final SourceAttachmentRepository sourceAttachmentRepository;
    private final SourceAttachmentStorage sourceAttachmentStorage;

    /**
     * 소스 식별자에 연결된 첨부 메타데이터를 조회한다.
     *
     * @param sourceId 소스 식별자
     * @return 등록 순서로 정렬한 첨부파일 요약 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<SourceAttachmentSummaryVo> list(long sourceId) {
        return this.sourceAttachmentRepository.list(sourceId);
    }

    /**
     * 소스에 속한 첨부를 확인하고 전체 또는 지정 범위의 원본을 연다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @param range 단일 HTTP Range 헤더 값. 전체 요청이면 null
     * @return 첨부 메타데이터와 열어 둔 원본 스트림
     * @throws ApiException 첨부를 찾을 수 없거나, 요청 범위가 유효하지 않거나, 원본을 열 수 없는 경우
     *         범위 오류에는 원본 크기를 담은 Content-Range 헤더를 포함한다.
     */
    @Override
    public ReadableAttachment deliver(long sourceId, UUID attachmentId, String range) {
        StoredAttachmentVo stored = this.sourceAttachmentRepository.find(sourceId, attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));
        long size = stored.getSize();

        if (range == null) {
            return new ReadableAttachment(stored.getName(), stored.getContentType(),
                    openOriginal(stored.getKey(), null), size, null);
        }

        long first;
        long last;
        try {
            HttpRange requested = parseRange(range);
            first = requested.getRangeStart(size);
            last = requested.getRangeEnd(size);
            if (first < 0 || last < first || first >= size) {
                throw new IllegalArgumentException("Byte range is outside the attachment.");
            }
        } catch (IllegalArgumentException exception) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
            throw new ApiException(ErrorCode.INVALID_RANGE, Map.of(), headers, null);
        }

        String objectRange = "bytes=" + first + "-" + last;
        String contentRange = "bytes " + first + "-" + last + "/" + size;
        return new ReadableAttachment(stored.getName(), stored.getContentType(),
                openOriginal(stored.getKey(), objectRange), last - first + 1, contentRange);
    }

    /**
     * 바이트 범위를 해석하고 올바른 단일 범위만 허용한다.
     *
     * @param range 단일 HTTP Range 헤더 값
     * @return HTTP 헤더에서 해석한 바이트 범위
     * @throws IllegalArgumentException 헤더 형식이 잘못됐거나 범위가 한 개가 아닌 경우
     */
    private static HttpRange parseRange(String range) {
        List<HttpRange> ranges = HttpRange.parseRanges(range);
        if (ranges.size() != 1) {
            throw new IllegalArgumentException("Exactly one byte range is required.");
        }
        return ranges.getFirst();
    }

    /**
     * 전체 또는 지정 범위의 원본을 열고 저장소 실패를 공통 오류 코드로 변환한다.
     *
     * @param key S3 객체 키
     * @param range 단일 HTTP Range 헤더 값. 전체 요청이면 null
     * @return 호출자가 사용 후 닫아야 하는 원본 입력 스트림
     * @throws ApiException S3에서 원본 스트림을 열 수 없는 경우
     */
    private InputStream openOriginal(String key, String range) {
        try {
            return this.sourceAttachmentStorage.read(key, range);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }
}
