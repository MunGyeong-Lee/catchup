package com.moon.catchup.careeritem.service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentSummaryVo;
import com.moon.catchup.careeritem.domain.StoredAttachmentVo;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.careeritem.storage.CareerItemAttachmentStorage;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 자료에 연결된 첨부 목록과 원본을 열기 위한 정보를 제공한다. */
@Service
@RequiredArgsConstructor
public class CareerItemAttachmentServiceImpl implements CareerItemAttachmentService {
    private final CareerItemAttachmentRepository careerItemAttachmentRepository;
    private final CareerItemAttachmentStorage careerItemAttachmentStorage;

    /** 자료 식별자에 연결된 첨부 메타데이터를 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public List<CareerItemAttachmentSummaryVo> list(long careerItemId) {
        return this.careerItemAttachmentRepository.list(careerItemId);
    }

    /** 자료에 속한 첨부를 확인하고 전체 또는 지정 범위의 원본을 연다. */
    @Override
    public ReadableAttachment deliver(long careerItemId, UUID attachmentId, String range) {
        StoredAttachmentVo stored = this.careerItemAttachmentRepository.find(careerItemId, attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));
        long size = stored.getSize();

        if (range == null) {
            return new ReadableAttachment(stored.getName(), stored.getContentType(),
                    openOriginal(stored.getKey(), null), size, null);
        }

        HttpRange requested = parseRange(range, size);
        long first;
        long last;
        try {
            first = requested.getRangeStart(size);
            last = requested.getRangeEnd(size);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRange(size);
        }
        if (first < 0 || last < first || first >= size) throw ApiException.invalidRange(size);

        String objectRange = "bytes=" + first + "-" + last;
        String contentRange = "bytes " + first + "-" + last + "/" + size;
        return new ReadableAttachment(stored.getName(), stored.getContentType(),
                openOriginal(stored.getKey(), objectRange), last - first + 1, contentRange);
    }

    /** 바이트 범위를 해석하고 올바른 단일 범위만 허용한다. */
    private static HttpRange parseRange(String range, long size) {
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(range);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRange(size);
        }
        if (ranges.size() != 1) throw ApiException.invalidRange(size);
        return ranges.getFirst();
    }

    /** 전체 또는 지정 범위의 원본을 열고 저장소 실패를 공통 오류 코드로 변환한다. */
    private InputStream openOriginal(String key, String range) {
        try {
            return this.careerItemAttachmentStorage.read(key, range);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }
}
