package com.moon.catchup.domain.source;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** DB에서 조회한 업로드의 S3 예약 키, 대상 소스, 완료 상태를 담는다. */
@Getter
@AllArgsConstructor
public final class UploadAttemptVo {
    private final List<String> objectKeys;
    private final Long sourceId;
    private final Long targetSourceId;
    private final boolean completed;

    /**
     * 기존 등록 흐름과 같은 방식으로 등록 시도 상태를 생성한다.
     *
     * @param objectKeys 줄바꿈으로 구분한 예약 객체 키
     * @param sourceId 소스 식별자
     */
    public UploadAttemptVo(List<String> objectKeys, Long sourceId) {
        this(objectKeys, sourceId, null, sourceId != null);
    }
}
