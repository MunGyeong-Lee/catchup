package com.moon.catchup.controller.source.dto;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.moon.catchup.domain.source.SourceDateFormat;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * 소스 등록 요청의 공통 입력과 유형별 바인딩을 정의한다.
 *
 * <p>지원하지 않거나 누락된 유형은 공통 DTO로 읽어 서비스에서 기존 필드 오류를 반환한다.
 * 유형에 정의되지 않은 키와 잘못된 값의 원래 형태도 유지해 서비스의 검증 순서를 적용한다.
 */
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "sourceType", visible = true, defaultImpl = SourceRegisterRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CareerHistoryRegisterRequest.class, name = "CAREER_HISTORY"),
        @JsonSubTypes.Type(value = ExperienceRegisterRequest.class, name = "EXPERIENCE"),
        @JsonSubTypes.Type(value = CertificationRegisterRequest.class, name = "CERTIFICATION")
})
@Schema(description = "소스 유형별 등록 요청", discriminatorProperty = "sourceType",
        oneOf = {CareerHistoryRegisterRequest.class, ExperienceRegisterRequest.class, CertificationRegisterRequest.class},
        discriminatorMapping = {
                @DiscriminatorMapping(value = "CAREER_HISTORY", schema = CareerHistoryRegisterRequest.class),
                @DiscriminatorMapping(value = "EXPERIENCE", schema = ExperienceRegisterRequest.class),
                @DiscriminatorMapping(value = "CERTIFICATION", schema = CertificationRegisterRequest.class)
        })
public sealed class SourceRegisterRequest
        permits CareerHistoryRegisterRequest, ExperienceRegisterRequest, CertificationRegisterRequest {
    @JsonProperty
    @Schema(description = "소스 유형", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"CAREER_HISTORY", "EXPERIENCE", "CERTIFICATION"})
    private String sourceType;

    @JsonProperty
    @Schema(description = "공통 소스 이름", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object name;

    @Getter(AccessLevel.NONE)
    private final Map<String, Object> unboundFields = new HashMap<>();

    /**
     * 선언되지 않은 입력을 서비스 검증까지 보존한다.
     *
     * @param field 요청의 최상위 필드 이름
     * @param value 요청에 들어온 값
     */
    @JsonAnySetter
    private void captureUnexpectedField(String field, Object value) {
        this.unboundFields.put(field, value);
    }

    /**
     * 최상위 상세 입력을 기존 내부 검증 및 저장 모델에 연결한다.
     *
     * @return 유형별 입력과 검증할 미정의 필드를 담은 변경 가능한 맵
     */
    public Map<String, Object> toDetails() {
        Map<String, Object> details = new HashMap<>();
        addDetails(details);
        details.putAll(this.unboundFields);
        return details;
    }

    /**
     * 유효한 날짜를 변환하고 잘못된 값은 서비스 검증 순서에 따라 보고할 수 있게 보존한다.
     *
     * @param <T> 변환할 날짜 또는 연월 타입
     * @param field 내부 검증에서 사용하는 상세 필드
     * @param value 원래 JSON 값
     * @param parser ISO 문자열을 변환하는 함수
     * @return 변환한 값 또는 입력이 비어 있거나 유효하지 않으면 null
     */
    protected <T> T bindDate(String field, Object value, Function<String, T> parser) {
        this.unboundFields.remove(field);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return parser.apply(text.strip());
            } catch (DateTimeParseException exception) {
                this.unboundFields.put(field, SourceDateFormat.Invalid.VALUE);
            }
        } else {
            this.unboundFields.put(field, value);
        }
        return null;
    }

    /**
     * 선언된 유형별 필드를 내부 상세 입력에 추가한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    protected void addDetails(Map<String, Object> details) {
        // 유형을 바인딩하지 못한 요청은 서비스에서 소스 유형 오류로 처리한다.
    }
}
