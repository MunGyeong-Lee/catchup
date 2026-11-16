package com.moon.catchup.careeritem.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** DB에서 조회한 업로드의 S3 예약 키, 대상 자료, 완료 상태를 담는다. */
@Getter
@AllArgsConstructor
public final class UploadAttemptVo {
    private final List<String> objectKeys;
    private final Long careerItemId;
    private final Long targetCareerItemId;
    private final boolean completed;

    /** 기존 등록 흐름과 같은 방식으로 등록 시도 상태를 생성한다. */
    public UploadAttemptVo(List<String> objectKeys, Long careerItemId) {
        this(objectKeys, careerItemId, null, careerItemId != null);
    }
}
