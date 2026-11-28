package com.moon.catchup.domain.source;

import java.util.List;

import lombok.Getter;

/** DB에서 조회한 업로드의 S3 예약 키, 대상 소스, 완료 상태를 담는다. */
@Getter
public final class UploadAttemptVo {
    private final List<String> objectKeys;
    private final Long sourceId;
    private final Long targetSourceId;
    private final boolean completed;

    /**
     * 예약 객체 키와 대상 소스, 완료 상태를 초기화한다.
     *
     * @param objectKeys 예약 객체 키 목록
     * @param sourceId 업로드를 완료한 소스 식별자
     * @param targetSourceId 수정 대상 소스 식별자. 신규 등록이면 null
     * @param completed 업로드 완료 여부
     */
    private UploadAttemptVo(List<String> objectKeys, Long sourceId, Long targetSourceId, boolean completed) {
        this.objectKeys = objectKeys;
        this.sourceId = sourceId;
        this.targetSourceId = targetSourceId;
        this.completed = completed;
    }

    /**
     * 저장된 업로드 예약 키와 대상 소스, 완료 상태를 담는 조회용 VO를 만든다.
     *
     * @param objectKeys 예약 객체 키 목록
     * @param sourceId 업로드를 완료한 소스 식별자
     * @param targetSourceId 수정 대상 소스 식별자. 신규 등록이면 null
     * @param completed 업로드 완료 여부
     * @return 업로드 예약 조회용 VO
     */
    public static UploadAttemptVo of(List<String> objectKeys, Long sourceId, Long targetSourceId, boolean completed) {
        return new UploadAttemptVo(objectKeys, sourceId, targetSourceId, completed);
    }

    /**
     * 신규 등록의 소스 식별자로 업로드 완료 상태를 결정한다.
     *
     * @param objectKeys 예약 객체 키 목록
     * @param sourceId 업로드를 완료한 소스 식별자
     * @return 신규 등록의 업로드 예약 조회용 VO
     */
    public static UploadAttemptVo of(List<String> objectKeys, Long sourceId) {
        return new UploadAttemptVo(objectKeys, sourceId, null, sourceId != null);
    }
}
