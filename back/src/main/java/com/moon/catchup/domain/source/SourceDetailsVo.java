package com.moon.catchup.domain.source;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 소스 유형과 검증을 마친 입력값을 묶는다.
 *
 * <p>연월과 달력 날짜는 각각 {@link YearMonth}와 {@link LocalDate}로 다룬다.
 * JSONB에는 기존의 ISO 문자열을 저장하며 두 값에는 시간대를 적용하지 않는다.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SourceDetailsVo {
    private final SourceType type;
    private final String name;
    private final Map<String, Object> fields;

    /**
     * 검증된 입력으로 DB에서 식별자를 받을 새 소스 엔티티를 만든다.
     *
     * @return 소스 엔티티
     */
    public SourceEntity convertToSourceEntity() {
        return new SourceEntity(this);
    }

    /**
     * DB에서 조회한 유형과 상세 JSON을 불변 값 객체로 변환한다.
     *
     * @param source 변환하거나 저장할 소스
     * @return 검증한 소스 상세 값 객체
     */
    public static SourceDetailsVo from(SourceEntity source) {
        Map<String, Object> fields = new HashMap<>(source.getDetails());
        fields.computeIfPresent("startMonth", (field, value) -> YearMonth.parse((String) value));
        fields.computeIfPresent("endMonth", (field, value) -> YearMonth.parse((String) value));
        fields.computeIfPresent("issuedOn", (field, value) -> LocalDate.parse((String) value));
        fields.computeIfPresent("expiresOn", (field, value) -> LocalDate.parse((String) value));
        return fromValidated(source.getType(), source.getName(), fields);
    }

    /**
     * 서비스에서 필수 항목과 소스 유형별 업무 규칙을 검증한 입력으로 값 객체를 만든다.
     *
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param fields 유형에 맞는 상세 필드와 값. 연월은 YearMonth, 달력 날짜는 LocalDate
     * @return 검증한 소스 상세 값 객체
     */
    public static SourceDetailsVo fromValidated(SourceType type, String name, Map<String, Object> fields) {
        return new SourceDetailsVo(Objects.requireNonNull(type), Objects.requireNonNull(name), Map.copyOf(fields));
    }

    /**
     * 날짜와 연월을 기존 JSONB 문자열 형식으로 변환한다.
     *
     * @return DB 저장용 상세 맵
     */
    public Map<String, Object> toStoredFields() {
        Map<String, Object> storedFields = new HashMap<>(this.fields);
        storedFields.replaceAll((field, value) ->
                value instanceof YearMonth || value instanceof LocalDate ? value.toString() : value);
        return Map.copyOf(storedFields);
    }
}
