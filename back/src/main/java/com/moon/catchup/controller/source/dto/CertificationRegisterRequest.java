package com.moon.catchup.controller.source.dto;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.moon.catchup.domain.source.SourceDateFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 자격증 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "자격증 소스 등록 요청")
public final class CertificationRegisterRequest extends SourceRegisterRequest {
    @JsonProperty
    @Schema(description = "자격증 이름", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object certificateName;

    @Schema(description = "취득일 (YYYY-MM-DD, 시간대 없음)", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate issuedOn;

    @JsonProperty
    @Schema(description = "발급 기관", type = "string")
    private Object issuer;

    @Schema(description = "만료일 (YYYY-MM-DD, 시간대 없음)", type = "string")
    private LocalDate expiresOn;

    @JsonProperty
    @Schema(description = "자격증 번호", type = "string")
    private Object certificateNumber;

    @JsonProperty
    @Schema(description = "확인 링크", type = "string")
    private Object verificationLink;

    /**
     * 등록 요청의 issuedOn 필드를 변환한다.
     *
     * @param value 이 필드에 들어온 원래 JSON 값
     */
    @JsonSetter("issuedOn")
    private void bindIssuedOn(Object value) {
        this.issuedOn = bindDate("issuedOn", value, SourceDateFormat::parseDate);
    }

    /**
     * 등록 요청의 expiresOn 필드를 변환한다.
     *
     * @param value 이 필드에 들어온 원래 JSON 값
     */
    @JsonSetter("expiresOn")
    private void bindExpiresOn(Object value) {
        this.expiresOn = bindDate("expiresOn", value, SourceDateFormat::parseDate);
    }

    /**
     * 자격증 등록 필드를 기존 상세 저장 키에 연결한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    @Override
    protected void addDetails(Map<String, Object> details) {
        details.put("name", this.certificateName);
        details.put("issuedOn", this.issuedOn);
        details.put("issuer", this.issuer);
        details.put("expiresOn", this.expiresOn);
        details.put("certificateNumber", this.certificateNumber);
        details.put("verificationLink", this.verificationLink);
    }
}
