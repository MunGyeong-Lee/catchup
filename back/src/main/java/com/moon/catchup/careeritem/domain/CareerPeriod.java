package com.moon.catchup.careeritem.domain;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;


/** 시작과 종료 값의 순서를 보장하는 기간 값 객체다. */
@Getter
@EqualsAndHashCode
public final class CareerPeriod<T extends Comparable<? super T>> {
    private final T start;
    private final T end;

    /** 시작과 종료가 모두 있으면 종료가 시작보다 빠른 값을 거부한다. */
    @Builder
    public CareerPeriod(T start, T end) {
        if (start != null && end != null && end.compareTo(start) < 0) {
            throw new IllegalArgumentException("End must be on or after start");
        }
        this.start = start;
        this.end = end;
    }
}
