package com.moon.catchup.careeritem.domain;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 자료 식별자와 검증된 상세 입력을 담는 도메인 객체다. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class CareerItemEntity {
    private final long id;
    private final CareerItemDetailsVo details;
}
