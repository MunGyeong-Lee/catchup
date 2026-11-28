package com.moon.catchup.controller.source;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moon.catchup.controller.source.dto.SourceRegisterRequest;
import com.moon.catchup.controller.source.dto.SourceResponse;
import com.moon.catchup.controller.source.dto.SourceUpdateRequest;
import com.moon.catchup.common.http.PageResponse;
import com.moon.catchup.common.http.ResponseVo;
import com.moon.catchup.controller.source.dto.SourceListResponse;
import com.moon.catchup.domain.source.SourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

/** 소스 목록, 등록, 상세 조회, 수정 및 삭제 API의 OpenAPI 문서를 정의한다. */
@Tag(name = "Sources")
public interface SourceDocs {
    /** JSON 본문과 multipart의 source 파트에 공통으로 적용하는 수정 계약이다. */
    public static final String SOURCE_UPDATE_INPUT =
            "sourceType, 공통 이름 name, 유형별 입력 전체를 담은 details를 보냅니다. 유형을 변경할 수 있습니다. "
                    + "지원회사(TARGET_COMPANY)는 details.companyName이 필수이며 jobTitle, content, "
                    + "idealCandidateProfile, jobDescription은 선택 문자열입니다. "
                    + "문체 및 어투(WRITING_STYLE)와 기타(OTHER_TEXT)는 details.content가 필수 문자열입니다. "
                    + "removeAttachmentIds에 지정한 기존 첨부만 제거하며 생략하면 모두 유지합니다. "
                    + "소스 식별자와 최초 등록 시각은 유지됩니다. "
                    + "새 첨부를 추가하려면 multipart/form-data의 source 파트에 같은 JSON을 보내고 "
                    + "requestId와 files를 함께 보냅니다. 같은 요청을 재시도할 때는 같은 requestId를 사용합니다. "
                    + "새 첨부 메타데이터와 소스 변경은 함께 확정됩니다. 제거한 원본은 커밋 후 비동기로 정리합니다.";

    /**
     * 소스 요약 목록을 반환하는 작업을 문서화한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 선택 정렬 조건
     * @param sourceType 선택 소스 유형. 전체 목록 조회 시 null
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 목록 조회",
            description = "소스 목록의 한 페이지를 조회합니다. 유형을 생략하거나 빈 값 또는 공백만 보내면 전체 목록을 조회합니다. "
                    + "유형을 지정하면 일치하는 결과를 기준으로 정렬하고 페이지와 전체 건수를 계산합니다. "
                    + "정렬 생략 시 최근 등록 순이며, 등록 시각은 UTC ISO 8601입니다. 항목마다 저장된 첨부파일 유무를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "소스 목록 페이지",
            content = @Content(schema = @Schema(implementation = SourcePageEnvelope.class)))
    @ApiResponse(responseCode = "400", description = "페이지, 정렬 또는 유형 매개변수가 유효하지 않음. "
            + "유형을 변환할 수 없으면 INVALID_REQUEST를 반환하며 field_errors는 생략됩니다.",
            content = @Content(schema = @Schema(implementation = ResponseVo.class)))
    public ResponseVo list(
            @Parameter(description = "페이지 번호", schema = @Schema(defaultValue = "1", minimum = "1")) int pageNumber,
            @Parameter(description = "페이지 크기",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100")) int pageSize,
            @Parameter(description = "쉼표로 구분한 property:ASC 또는 property:DESC 목록. "
                    + "허용 속성은 createdAt, sourceId, sourceType, name입니다. "
                    + "누락된 createdAt과 sourceId는 각 내림차순으로 덧붙입니다.",
                    example = "name:ASC,createdAt:DESC") String orderBy,
            @Parameter(description = "대문자 소스 유형 코드를 지정합니다. 반복된 키는 첫 값을 사용하고 코드의 앞뒤 공백은 제거합니다. "
                    + "쉼표로 연결한 코드는 유효하지 않습니다.") SourceType sourceType);

    /**
     * 파일 없이 소스를 등록하는 작업을 문서화한다.
     *
     * @param req 역직렬화한 요청 본문
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 등록",
            description = "JSON 본문에서 공통 이름과 유형별 입력을 최상위로 받습니다. 자격증 이름은 certificateName입니다. "
                    + "연월과 날짜는 ISO 문자열 또는 정수 배열 [연, 월]과 [연, 월, 일]을 받습니다. 빈 배열은 입력 없음으로 처리합니다.")
    @ApiResponse(responseCode = "201", description = "생성한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "미정의 JSON 키나 본문 형식 오류는 INVALID_REQUEST, 소스 입력 오류는 VALIDATION_FAILED를 반환합니다.")
    public ResponseVo register(SourceRegisterRequest req);

    /**
     * 소스와 선택한 파일을 함께 등록하는 작업을 문서화한다.
     *
     * @param req 역직렬화한 요청 본문
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param files 요청에 포함된 첨부파일 목록
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "첨부 파일이 있는 소스 등록",
            description = "source JSON 파트는 일반 등록과 동일한 최상위 입력과 날짜 변환 규칙을 사용합니다. "
                    + "requestId와 files도 전송합니다. 재시도 시 같은 requestId를 사용합니다.")
    @ApiResponse(responseCode = "201", description = "생성했거나 재시도로 확인한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "source 파트의 미정의 JSON 키나 형식 오류는 INVALID_REQUEST, 소스 입력 또는 첨부 검증 오류는 VALIDATION_FAILED를 반환합니다.")
    @ApiResponse(responseCode = "404", description = "재시도할 등록의 소스가 삭제됨")
    @ApiResponse(responseCode = "413", description = "첨부 파일 크기 제한 초과")
    @ApiResponse(responseCode = "503", description = "첨부 원본 저장소 오류")
    public ResponseVo registerAttachments(
            SourceRegisterRequest req, UUID requestId, List<MultipartFile> files);

    /**
     * 소스 상세와 첨부 목록 조회를 문서화한다.
     *
     * <p>공통 상세 필드와 유형별 저장값은 공통 응답의 response 안에 함께 반환한다.
     * 첨부 목록에는 공개 메타데이터만 포함하며 소스가 없으면 SOURCE_NOT_FOUND를 반환한다.
     *
     * @param sourceId 소스 식별자
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 상세 조회",
            description = "response 안에 공통 이름, 첨부 목록, 유형별 상세 필드를 같은 수준으로 전달합니다. "
                    + "createdAt 및 updatedAt은 UTC ISO 8601 문자열입니다. 자격증 이름은 certificateName으로 반환합니다. "
                    + "첨부가 없으면 빈 배열을 반환합니다. 첨부 정보는 attachmentId, name, contentType, size로 구성됩니다.")
    @ApiResponse(responseCode = "200", description = "저장된 소스 입력과 첨부 목록",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SourceDetailEnvelope.class),
                    examples = {
                            @ExampleObject(name = "TARGET_COMPANY", summary = "첨부가 있는 지원회사", value = """
                                    {
                                      "error": null,
                                      "error_message": null,
                                      "response": {
                                        "sourceId": 101,
                                        "sourceType": "TARGET_COMPANY",
                                        "name": "새봄 지원 자료",
                                        "createdAt": "2026-11-20T01:00:00Z",
                                        "updatedAt": "2026-11-21T01:00:00Z",
                                        "companyName": "새봄",
                                        "jobTitle": "백엔드 개발자",
                                        "content": "채용 공고의 주요 요건을 정리했습니다.",
                                        "idealCandidateProfile": "협업과 피드백을 중시하는 사람",
                                        "jobDescription": "서비스 API를 개발하고 운영합니다.",
                                        "attachments": [{
                                          "attachmentId": "78e5b499-bd94-4ae7-a9a7-56f33b6e127d",
                                          "name": "채용공고.pdf",
                                          "contentType": "application/pdf",
                                          "size": 2048
                                        }]
                                      }
                                    }
                                    """),
                            @ExampleObject(name = "WRITING_STYLE", summary = "문체 및 어투", value = """
                                    {
                                      "error": null,
                                      "error_message": null,
                                      "response": {
                                        "sourceId": 102,
                                        "sourceType": "WRITING_STYLE",
                                        "name": "자기소개서 문체",
                                        "createdAt": "2026-11-20T02:00:00Z",
                                        "updatedAt": "2026-11-20T02:00:00Z",
                                        "content": "짧은 문장으로 행동과 결과를 설명합니다.",
                                        "attachments": []
                                      }
                                    }
                                    """),
                            @ExampleObject(name = "OTHER_TEXT", summary = "기타 텍스트", value = """
                                    {
                                      "error": null,
                                      "error_message": null,
                                      "response": {
                                        "sourceId": 103,
                                        "sourceType": "OTHER_TEXT",
                                        "name": "면접 참고 노트",
                                        "createdAt": "2026-11-20T03:00:00Z",
                                        "updatedAt": "2026-11-20T03:00:00Z",
                                        "content": "프로젝트에서 맡은 역할과 성과를 요약했습니다.",
                                        "attachments": []
                                      }
                                    }
                                    """)
                    }))
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SourceDetailNotFoundEnvelope.class),
                    examples = @ExampleObject(name = "SOURCE_NOT_FOUND", value = """
                            {
                              "error": "SOURCE_NOT_FOUND",
                              "error_message": "소스를 찾을 수 없어요.",
                              "response": null
                            }
                            """)))
    public ResponseVo get(long sourceId);

    /**
     * 소스 입력 및 첨부 제거를 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @param req 역직렬화한 요청 본문
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 수정 (JSON 요청)",
            description = SOURCE_UPDATE_INPUT,
            requestBody = @RequestBody(required = true, content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SourceUpdateBody.class))))
    @ApiResponse(responseCode = "200", description = "수정한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "요청 형식, 입력 또는 제거 대상 첨부 오류. "
            + "DTO에 정의되지 않은 JSON 키는 INVALID_REQUEST, 입력 검증 오류는 VALIDATION_FAILED와 field_errors를 반환합니다.")
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음")
    public ResponseVo update(long sourceId,
                             @Parameter(schema = @Schema(implementation = SourceUpdateBody.class)) SourceUpdateRequest req);

    /**
     * 새 첨부를 추가하는 소스 수정을 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @param req 역직렬화한 요청 본문
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param files 요청에 포함된 첨부파일 목록
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 수정 (새 첨부파일 포함)",
            description = SOURCE_UPDATE_INPUT,
            requestBody = @RequestBody(required = true, content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = SourceUpdateBody.class)),
                    @Content(mediaType = "multipart/form-data",
                            schema = @Schema(implementation = SourceUpdateMultipartBody.class),
                            encoding = @Encoding(name = "source", contentType = "application/json"))
            }))
    @ApiResponse(responseCode = "200", description = "수정한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "요청 형식, 소스 입력 또는 첨부 파일 오류. "
            + "DTO에 정의되지 않은 JSON 키는 INVALID_REQUEST, 입력 검증 오류는 VALIDATION_FAILED와 field_errors를 반환합니다.")
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음")
    @ApiResponse(responseCode = "413", description = "첨부 파일 크기 제한 초과")
    @ApiResponse(responseCode = "503", description = "첨부 원본 저장소 오류")
    public ResponseVo updateAttachments(long sourceId, SourceUpdateRequest req,
                                        @Parameter(hidden = true) UUID requestId, List<MultipartFile> files);

    /**
     * 소스 전체 삭제를 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 삭제",
            description = "소스와 연결된 첨부 메타데이터를 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "소스를 삭제함")
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음")
    public ResponseVo delete(long sourceId);
    /** OpenAPI에 표시할 소스 목록 페이지의 응답 형식이다. */
    @Getter
    public static final class SourcePageEnvelope {
        private String error;
        @Schema(name = "error_message")
        private String errorMessage;
        private PageResponse<SourceListResponse> response;
    }

    /** OpenAPI에서 JSON 수정 본문과 multipart의 source 파트에 사용하는 스키마다. */
    @Getter
    public static final class SourceUpdateBody {
        @Schema(implementation = SourceType.class, requiredMode = Schema.RequiredMode.REQUIRED)
        private String sourceType;
        @Schema(description = "공통 소스 이름", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;
        @Schema(description = "저장할 유형별 입력 전체. 연월은 YYYY-MM, 달력 날짜는 YYYY-MM-DD 문자열입니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Map<String, Object> details;
        @Schema(description = "제거할 기존 첨부 식별자")
        private List<UUID> removeAttachmentIds;
    }

    /** OpenAPI에서 새 첨부와 함께 보낼 수정 폼을 설명한다. */
    @Getter
    public static final class SourceUpdateMultipartBody {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private SourceUpdateBody source;
        @Schema(description = "동일한 수정 요청을 재시도할 때 재사용하는 식별자",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID requestId;
        @Schema(description = "추가할 첨부 파일", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<MultipartFile> files;
    }

    /** OpenAPI에 표시할 소스 상세 조회의 응답 형식이다. */
    @Getter
    public static final class SourceDetailEnvelope {
        @Schema(types = "null", requiredMode = Schema.RequiredMode.REQUIRED)
        private String error;
        @Schema(name = "error_message", types = "null", requiredMode = Schema.RequiredMode.REQUIRED)
        private String errorMessage;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private SourceResponse response;
    }

    /** OpenAPI에 표시할 소스 상세 조회의 공통 자료 없음 오류 형식이다. */
    @Getter
    public static final class SourceDetailNotFoundEnvelope {
        @Schema(allowableValues = "SOURCE_NOT_FOUND", requiredMode = Schema.RequiredMode.REQUIRED)
        private String error;
        @Schema(name = "error_message", requiredMode = Schema.RequiredMode.REQUIRED)
        private String errorMessage;
        @Schema(types = "null", requiredMode = Schema.RequiredMode.REQUIRED)
        private Object response;
    }
}
