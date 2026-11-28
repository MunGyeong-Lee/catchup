package com.moon.catchup.domain.source;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** 소스 연월과 달력 날짜의 입력 형식을 정의한다. */
public final class SourceDateFormat {
    /** 공통 파서의 인스턴스 생성을 막는다. */
    private SourceDateFormat() {}

    /** 날짜 바인딩에서 형식이나 달력 값이 올바르지 않았음을 나타낸다. */
    public enum Invalid {
        /** 서비스 검증 순서가 해당 값에 이르렀을 때 날짜 오류를 반환한다. */
        VALUE
    }

    /**
     * 시간대가 없는 연월을 읽는다.
     *
     * @param value YYYY-MM 문자열
     * @return 읽은 연월
     * @throws DateTimeParseException 연월이 올바르지 않은 경우
     */
    public static YearMonth parseMonth(String value) {
        requireFormat(value, "\\d{4}-\\d{2}");
        return YearMonth.parse(value);
    }

    /**
     * 시간대가 없는 달력 날짜를 읽는다.
     *
     * @param value YYYY-MM-DD 문자열
     * @return 읽은 날짜
     * @throws DateTimeParseException 날짜가 올바르지 않은 경우
     */
    public static LocalDate parseDate(String value) {
        requireFormat(value, "\\d{4}-\\d{2}-\\d{2}");
        return LocalDate.parse(value);
    }

    /**
     * 0001년부터 시작하는 고정 길이 ISO 입력인지 확인한다.
     *
     * @param value 입력 문자열
     * @param pattern 허용 형식의 정규식
     * @throws DateTimeParseException 형식이 올바르지 않은 경우
     */
    private static void requireFormat(String value, String pattern) {
        if (!value.matches(pattern) || value.startsWith("0000")) {
            throw new DateTimeParseException("Invalid source date format", value, 0);
        }
    }
}
