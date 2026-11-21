package com.moon.catchup.controller.source.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 자격증 소스의 상세 정보를 전달한다. */
@Getter
@Schema(description = "자격증 소스 상세 응답")
public final class CertificationResponse extends SourceResponse {
    @Schema(description = "자격증 이름")
    private final String certificateName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "취득일 (YYYY-MM-DD)", type = "string")
    private final LocalDate issuedOn;
    @Schema(description = "발급 기관")
    private final String issuer;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "만료일 (YYYY-MM-DD)", type = "string")
    private final LocalDate expiresOn;
    @Schema(description = "자격증 번호")
    private final String certificateNumber;
    @Schema(description = "확인 링크")
    private final String verificationLink;

    /**
     * 저장된 자격증 입력값을 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    public CertificationResponse(SourceEditVo edit) {
        super(edit);
        Map<String, Object> fields = edit.getDetails().getFields();
        this.certificateName = Objects.toString(fields.get("name"), null);
        this.issuedOn = (LocalDate) fields.get("issuedOn");
        this.issuer = Objects.toString(fields.get("issuer"), null);
        this.expiresOn = (LocalDate) fields.get("expiresOn");
        this.certificateNumber = Objects.toString(fields.get("certificateNumber"), null);
        this.verificationLink = Objects.toString(fields.get("verificationLink"), null);
    }
}
