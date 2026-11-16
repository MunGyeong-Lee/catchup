package com.moon.catchup.careeritem.domain;

import java.util.Map;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 자료 유형과 해당 유형에서 검증을 마친 입력값을 묶는 불변 값 객체다. */
@Getter
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class CareerItemDetailsVo {
    private final CareerItemType type;
    private final Map<String, Object> fields;

    /**
     * 검증된 상세 입력에 자료 ID를 연결해 엔티티를 만든다.
     *
     * @param careerItemId DB가 발급한 자료 ID
     * @return 자료 엔티티
     */
    public CareerItemEntity convertToCareerItemEntity(long careerItemId) {
        return new CareerItemEntity(careerItemId, this);
    }

    /** 서비스에서 필수 항목과 자료 유형별 업무 규칙을 검증한 입력으로 값 객체를 만든다. */
    public static CareerItemDetailsVo fromValidated(CareerItemType type, Map<String, Object> fields) {
        return new CareerItemDetailsVo(Objects.requireNonNull(type), Map.copyOf(fields));
    }
}
