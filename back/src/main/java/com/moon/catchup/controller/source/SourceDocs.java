package com.moon.catchup.controller.source;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.controller.source.dto.SourceRegisterRequest;
import com.moon.catchup.controller.source.dto.SourceResponse;
import com.moon.catchup.controller.source.dto.SourceUpdateRequest;
import com.moon.catchup.common.http.PageResponse;
import com.moon.catchup.common.http.ResponseVo;
import com.moon.catchup.controller.source.dto.SourceListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

/** 소스 목록, 등록, 상세 조회, 수정 및 삭제 API의 OpenAPI 문서를 정의한다. */
@Tag(name = "Sources")
public interface SourceDocs {
    /**
     * 소스 요약 목록을 반환하는 작업을 문서화한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 선택 정렬 조건
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 목록 조회",
            description = "이력, 경험, 자격증, 지원회사, 문체 및 어투, 기타 소스 목록의 한 페이지를 조회합니다. 정렬 생략 시 최근 등록 순이며, 등록 시각은 UTC ISO 8601입니다. 항목마다 저장된 첨부파일 유무를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "소스 목록 페이지",
            content = @Content(schema = @Schema(implementation = SourcePageEnvelope.class)))
    @ApiResponse(responseCode = "400", description = "페이지 또는 정렬 매개변수가 유효하지 않음")
    public ResponseVo list(
            @Parameter(description = "페이지 번호", schema = @Schema(defaultValue = "1", minimum = "1")) int pageNumber,
            @Parameter(description = "페이지 크기",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100")) int pageSize,
            @Parameter(description = "쉼표로 구분한 property:ASC 또는 property:DESC 목록. "
                    + "허용 속성은 createdAt, sourceId, sourceType, name입니다. "
                    + "누락된 createdAt과 sourceId는 각 내림차순으로 덧붙입니다.",
                    example = "name:ASC,createdAt:DESC") String orderBy);

    /**
     * 파일 없이 소스를 등록하는 작업을 문서화한다.
     *
     * @param req 역직렬화한 요청 본문
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 등록",
            description = "JSON 본문에서 공통 이름과 유형별 입력을 최상위로 받습니다. 자격증 이름은 certificateName입니다.")
    @ApiResponse(responseCode = "201", description = "생성한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "요청 형식 또는 소스 입력 오류")
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
            description = "source JSON 파트는 일반 등록과 동일한 최상위 입력을 사용합니다. requestId와 files도 전송합니다. 재시도 시 같은 requestId를 사용합니다.")
    @ApiResponse(responseCode = "201", description = "생성했거나 재시도로 확인한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "요청 형식, 소스 입력 또는 첨부 파일 오류")
    @ApiResponse(responseCode = "404", description = "재시도할 등록의 소스가 삭제됨")
    @ApiResponse(responseCode = "413", description = "첨부 파일 크기 제한 초과")
    @ApiResponse(responseCode = "503", description = "첨부 원본 저장소 오류")
    public ResponseVo registerAttachments(
            SourceRegisterRequest req, UUID requestId, List<MultipartFile> files);

    /**
     * 소스 상세와 첨부 목록 조회를 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 상세 조회",
            description = "공통 이름과 첨부 목록 및 유형별 상세 필드를 응답의 같은 수준에 전달합니다. createdAt 및 updatedAt은 UTC ISO 8601 문자열입니다. 자격증 이름은 certificateName으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "저장된 소스 입력과 첨부 목록",
            content = @Content(schema = @Schema(implementation = SourceDetailEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음")
    public ResponseVo get(long sourceId);

    /**
     * 소스 입력 및 첨부 제거를 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @param req 역직렬화한 요청 본문
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "소스 수정",
            description = "JSON 본문으로 공통 이름과 유형별 상세 입력 전체 및 삭제할 첨부 식별자를 받습니다.")
    @ApiResponse(responseCode = "200", description = "수정한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "요청 형식, 입력 또는 제거 대상 첨부 오류")
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음")
    public ResponseVo update(long sourceId, SourceUpdateRequest req);

    /**
     * 새 첨부를 추가하는 소스 수정을 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @param req 역직렬화한 요청 본문
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param files 요청에 포함된 첨부파일 목록
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "새 첨부 파일이 있는 소스 수정",
            description = "source JSON 파트, requestId 및 새 files 파트를 받습니다.")
    @ApiResponse(responseCode = "200", description = "수정한 소스 식별자")
    @ApiResponse(responseCode = "400", description = "요청 형식, 소스 입력 또는 첨부 파일 오류")
    @ApiResponse(responseCode = "404", description = "소스가 존재하지 않음")
    @ApiResponse(responseCode = "413", description = "첨부 파일 크기 제한 초과")
    @ApiResponse(responseCode = "503", description = "첨부 원본 저장소 오류")
    public ResponseVo updateAttachments(long sourceId, SourceUpdateRequest req, UUID requestId,
                                 List<MultipartFile> files);

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

    /** OpenAPI에 표시할 소스 상세 조회의 응답 형식이다. */
    @Getter
    public static final class SourceDetailEnvelope {
        private String error;
        @Schema(name = "error_message")
        private String errorMessage;
        private SourceResponse response;
    }
}
