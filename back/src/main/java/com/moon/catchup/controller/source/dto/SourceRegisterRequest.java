package com.moon.catchup.controller.source.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 소스 등록 요청의 공통 입력과 유형별 바인딩을 정의한다.
 *
 * <p>DTO에 선언되지 않은 키는 Jackson에서 거절한다. 선언된 값의 원래 형태는
 * 서비스 검증 순서에 맞춰 형식과 필수 여부를 확인할 수 있도록 보존한다.
 * JSON 본문과 multipart의 source 파트에 동일한 최상위 필드와 필수 입력을 적용한다.
 */
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "sourceType", visible = true, defaultImpl = SourceRegisterRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CareerHistoryRegisterRequest.class, name = "CAREER_HISTORY"),
        @JsonSubTypes.Type(value = ExperienceRegisterRequest.class, name = "EXPERIENCE"),
        @JsonSubTypes.Type(value = CertificationRegisterRequest.class, name = "CERTIFICATION"),
        @JsonSubTypes.Type(value = TargetCompanyRegisterRequest.class, name = "TARGET_COMPANY"),
        @JsonSubTypes.Type(value = WritingStyleRegisterRequest.class, name = "WRITING_STYLE"),
        @JsonSubTypes.Type(value = OtherTextRegisterRequest.class, name = "OTHER_TEXT")
})
@Schema(description = "소스 유형별 등록 요청", discriminatorProperty = "sourceType",
        oneOf = {CareerHistoryRegisterRequest.class, ExperienceRegisterRequest.class, CertificationRegisterRequest.class,
                TargetCompanyRegisterRequest.class, WritingStyleRegisterRequest.class, OtherTextRegisterRequest.class},
        discriminatorMapping = {
                @DiscriminatorMapping(value = "CAREER_HISTORY", schema = CareerHistoryRegisterRequest.class),
                @DiscriminatorMapping(value = "EXPERIENCE", schema = ExperienceRegisterRequest.class),
                @DiscriminatorMapping(value = "CERTIFICATION", schema = CertificationRegisterRequest.class),
                @DiscriminatorMapping(value = "TARGET_COMPANY", schema = TargetCompanyRegisterRequest.class),
                @DiscriminatorMapping(value = "WRITING_STYLE", schema = WritingStyleRegisterRequest.class),
                @DiscriminatorMapping(value = "OTHER_TEXT", schema = OtherTextRegisterRequest.class)
        })
public sealed class SourceRegisterRequest
        permits CareerHistoryRegisterRequest, ExperienceRegisterRequest, CertificationRegisterRequest,
                TargetCompanyRegisterRequest, WritingStyleRegisterRequest, OtherTextRegisterRequest {
    /** 소스 유형 */
    @JsonProperty
    @Schema(description = "소스 유형", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"CAREER_HISTORY", "EXPERIENCE", "CERTIFICATION",
                    "TARGET_COMPANY", "WRITING_STYLE", "OTHER_TEXT"})
    private String sourceType;

    /** 공통 소스 이름 */
    @JsonProperty
    @Schema(description = "공통 소스 이름", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object name;

    /**
     * 최상위 상세 입력을 기존 내부 검증 및 저장 모델에 연결한다.
     *
     * @return 유형별 입력을 담은 변경 가능한 맵
     */
    public Map<String, Object> toDetails() {
        Map<String, Object> details = new HashMap<>();
        addDetails(details);
        return details;
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
