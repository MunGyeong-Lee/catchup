package com.moon.catchup.domain.source;

import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이력, 경험, 자격증과 각 소스 유형의 입력 항목을 정의한다. */
@Getter
@RequiredArgsConstructor
public enum SourceType {
    /** 소속과 활동 기간을 담는 이력이다. */
    CAREER_HISTORY(
            List.of("organization", "historyType", "startMonth"),
            Set.of("role", "endMonth", "ongoing", "description")),
    /** 사용자의 활동과 기여를 담는 경험이다. */
    EXPERIENCE(
            List.of("title", "contribution"),
            Set.of("startMonth", "endMonth", "organization", "outcome", "technologies", "relatedLink")),
    /** 자격증 이름과 취득일을 담는 자격증이다. */
    CERTIFICATION(
            List.of("name", "issuedOn"),
            Set.of("issuer", "expiresOn", "certificateNumber", "verificationLink"));

    private final List<String> requiredFields;
    private final Set<String> optionalFields;

    /**
     * 요청 문자열을 소스 유형으로 변환하고 지원하지 않는 값이면 null을 반환한다.
     *
     * @param value 요청에 들어온 유형 코드
     * @return 일치하는 유형. null 또는 지원하지 않는 코드이면 null
     */
    public static SourceType fromRequest(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 입력 항목이 해당 소스 유형의 필수 또는 선택 항목인지 확인한다.
     *
     * @param field 상세 입력의 필드 이름
     * @return 해당 유형의 필수 또는 선택 항목이면 true
     */
    public boolean acceptsField(String field) {
        return this.requiredFields.contains(field) || this.optionalFields.contains(field);
    }
}
