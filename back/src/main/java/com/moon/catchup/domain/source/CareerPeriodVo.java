package com.moon.catchup.domain.source;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;


/**
 * 시작과 종료 값의 순서를 보장하는 기간 값 객체다.
 *
 * @param <T> 비교 가능한 날짜 또는 연월 타입
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class CareerPeriodVo<T extends Comparable<? super T>> {
    private final T start;
    private final T end;

    /**
     * 시작과 종료의 순서를 검증하고 기간을 생성한다.
     *
     * @param <T> 비교 가능한 기간 값의 타입
     * @param start 시작값
     * @param end 종료값
     * @return 검증된 기간
     * @throws IllegalArgumentException 종료값이 시작값보다 빠를 때
     */
    public static <T extends Comparable<? super T>> CareerPeriodVo<T> of(T start, T end) {
        if (start != null && end != null && end.compareTo(start) < 0) {
            throw new IllegalArgumentException("End must be on or after start");
        }
        return new CareerPeriodVo<>(start, end);
    }
}
