package com.moon.catchup.careeritem.domain;

import java.util.Map;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 자료 유형과 해당 유형에서 검증을 마친 입력값을 묶는 불변 값 객체다. */
@Getter
@EqualsAndHashCode
public final class CareerItemDetails {
    private final CareerItemType type;
    private final Map<String, Object> fields;

    /** 자료 유형을 지정하고 호출자가 원본 입력을 바꿔도 영향을 받지 않도록 복사한다. */
    @Builder(access = AccessLevel.PRIVATE)
    private CareerItemDetails(CareerItemType type, Map<String, Object> fields) {
        this.type = Objects.requireNonNull(type);
        this.fields = Map.copyOf(fields);
    }

    /** 서비스에서 필수 항목과 자료 유형별 업무 규칙을 검증한 입력으로 값 객체를 만든다. */
    public static CareerItemDetails fromValidated(CareerItemType type, Map<String, Object> fields) {
        return builder().type(type).fields(fields).build();
    }
}
