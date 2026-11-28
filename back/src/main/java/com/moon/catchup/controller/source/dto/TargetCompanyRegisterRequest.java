package com.moon.catchup.controller.source.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 지원회사 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "지원회사 소스 등록 요청")
public final class TargetCompanyRegisterRequest extends SourceRegisterRequest {
    /** 회사명 */
    @JsonProperty
    @Schema(description = "회사명", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object companyName;

    /** 지원 직무 */
    @JsonProperty
    @Schema(description = "지원 직무", type = "string")
    private Object jobTitle;

    /** 회사·공고 메모 */
    @JsonProperty
    @Schema(description = "회사·공고 메모", type = "string")
    private Object content;

    /** 인재상 */
    @JsonProperty
    @Schema(description = "인재상", type = "string")
    private Object idealCandidateProfile;

    /** 직무 기술서 */
    @JsonProperty
    @Schema(description = "직무 기술서", type = "string")
    private Object jobDescription;

    /**
     * 지원회사 등록의 최상위 입력을 상세 저장 키에 연결한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    @Override
    protected void addDetails(Map<String, Object> details) {
        details.put("companyName", this.companyName);
        details.put("jobTitle", this.jobTitle);
        details.put("content", this.content);
        details.put("idealCandidateProfile", this.idealCandidateProfile);
        details.put("jobDescription", this.jobDescription);
    }
}
