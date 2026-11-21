package com.moon.catchup.controller.source.dto;

import java.util.Map;
import java.util.Objects;

import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 지원회사 소스의 상세 정보를 전달한다. */
@Getter
@Schema(description = "지원회사 소스 상세 응답")
public final class TargetCompanyResponse extends SourceResponse {
    @Schema(description = "회사명")
    private final String companyName;
    @Schema(description = "지원 직무")
    private final String jobTitle;
    @Schema(description = "회사·공고 메모")
    private final String content;
    @Schema(description = "인재상")
    private final String idealCandidateProfile;
    @Schema(description = "직무 기술서")
    private final String jobDescription;

    /**
     * 저장된 지원회사 입력값을 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    public TargetCompanyResponse(SourceEditVo edit) {
        super(edit);
        Map<String, Object> fields = edit.getDetails().getFields();
        this.companyName = Objects.toString(fields.get("companyName"), null);
        this.jobTitle = Objects.toString(fields.get("jobTitle"), null);
        this.content = Objects.toString(fields.get("content"), null);
        this.idealCandidateProfile = Objects.toString(fields.get("idealCandidateProfile"), null);
        this.jobDescription = Objects.toString(fields.get("jobDescription"), null);
    }
}
