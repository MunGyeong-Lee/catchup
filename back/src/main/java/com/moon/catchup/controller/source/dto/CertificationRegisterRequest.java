package com.moon.catchup.controller.source.dto;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 자격증 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "자격증 소스 등록 요청")
public final class CertificationRegisterRequest extends SourceRegisterRequest {
    /** 자격증 이름 */
    @JsonProperty
    @Schema(description = "자격증 이름", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object certificateName;

    /** 취득일 (YYYY-MM-DD, 시간대 없음) */
    @JsonProperty
    @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    @Schema(description = "취득일 (YYYY-MM-DD, 시간대 없음)", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate issuedOn;

    /** 발급 기관 */
    @JsonProperty
    @Schema(description = "발급 기관", type = "string")
    private Object issuer;

    /** 만료일 (YYYY-MM-DD, 시간대 없음) */
    @JsonProperty
    @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    @Schema(description = "만료일 (YYYY-MM-DD, 시간대 없음)", type = "string")
    private LocalDate expiresOn;

    /** 자격증 번호 */
    @JsonProperty
    @Schema(description = "자격증 번호", type = "string")
    private Object certificateNumber;

    /** 확인 링크 */
    @JsonProperty
    @Schema(description = "확인 링크", type = "string")
    private Object verificationLink;

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
