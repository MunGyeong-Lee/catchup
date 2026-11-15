package com.moon.catchup.careeritem.domain;

import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이력, 경험, 자격증과 각 자료 유형의 입력 항목을 정의한다. */
@Getter
@RequiredArgsConstructor
public enum CareerItemType {
    CAREER_HISTORY(
            List.of("organization", "historyType", "startMonth"),
            Set.of("role", "endMonth", "ongoing", "description")),
    EXPERIENCE(
            List.of("title", "contribution"),
            Set.of("startMonth", "endMonth", "organization", "outcome", "technologies", "relatedLink")),
    CERTIFICATION(
            List.of("name", "issuedOn"),
            Set.of("issuer", "expiresOn", "certificateNumber", "verificationLink"));

    private final List<String> requiredFields;
    private final Set<String> optionalFields;

    /** 요청 문자열을 자료 유형으로 변환하고 지원하지 않는 값이면 null을 반환한다. */
    public static CareerItemType fromRequest(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 입력 항목이 해당 자료 유형의 필수 또는 선택 항목인지 확인한다. */
    public boolean acceptsField(String field) {
        return this.requiredFields.contains(field) || this.optionalFields.contains(field);
    }
}
