package com.moon.catchup.careeritem.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.moon.catchup.careeritem.domain.CareerItemEntity;
import com.moon.catchup.careeritem.domain.CareerItemDetailsVo;
import com.moon.catchup.careeritem.domain.CareerItemSummaryVo;
import com.moon.catchup.careeritem.domain.CareerItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

/** DB의 career_items에서 자료 입력을 저장·조회·수정·삭제하고 목록 표시용 요약을 조회한다. */
@Repository
@RequiredArgsConstructor
public class CareerItemRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 검증된 자료 입력을 DB에 저장한다. */
    public CareerItemEntity insert(CareerItemDetailsVo details) {
        Long id = this.jdbcTemplate.queryForObject("""
                INSERT INTO career_items (career_item_type, details)
                VALUES (?, CAST(? AS jsonb))
                RETURNING career_item_id
                """, Long.class, details.getType().name(), this.objectMapper.writeValueAsString(details.getFields()));
        return details.convertToCareerItemEntity(id);
    }

    /** 선택한 자료의 저장된 입력을 읽고 쓰기 작업에서는 자료 행을 잠근다. */
    public Optional<CareerItemEntity> find(long id, boolean lock) {
        String query = "SELECT career_item_id, career_item_type, details FROM career_items WHERE career_item_id = ?";
        return this.jdbcTemplate.query(lock ? query + " FOR UPDATE" : query, this::convertToCareerItemEntity, id)
                .stream().findFirst();
    }

    /** 식별자와 최초 등록 시각을 유지하며 자료 유형과 세부 입력을 교체한다. */
    public void update(long id, CareerItemDetailsVo details) {
        this.jdbcTemplate.update("""
                UPDATE career_items SET career_item_type = ?, details = CAST(? AS jsonb) WHERE career_item_id = ?
                """, details.getType().name(), this.objectMapper.writeValueAsString(details.getFields()), id);
    }

    /** 첨부와 완료 요청의 연결을 정리한 자료 행을 삭제한다. */
    public void delete(long id) {
        this.jdbcTemplate.update("DELETE FROM career_items WHERE career_item_id = ?", id);
    }

    /** 최근 등록 순으로 자료 유형별 표시 정보를 조회한다. */
    public List<CareerItemSummaryVo> list() {
        return this.jdbcTemplate.query("""
                SELECT career_item_id, career_item_type,
                    CASE career_item_type
                        WHEN 'CAREER_HISTORY' THEN details ->> 'organization'
                        WHEN 'EXPERIENCE' THEN details ->> 'title'
                        WHEN 'CERTIFICATION' THEN details ->> 'name'
                    END AS display_name,
                    details ->> 'historyType' AS history_type,
                    details ->> 'startMonth' AS start_month,
                    details ->> 'issuedOn' AS issued_on
                FROM career_items ORDER BY created_at DESC, career_item_id DESC
                """, this::convertToCareerItemSummaryVo);
    }

    /** DB에 저장된 자료 유형과 JSON 상세값을 자료 엔티티로 복원한다. */
    private CareerItemEntity convertToCareerItemEntity(ResultSet row, int rowNumber) throws SQLException {
        CareerItemType type = CareerItemType.valueOf(row.getString("career_item_type"));
        Map<String, Object> fields = this.objectMapper.readValue(
                row.getString("details"), new TypeReference<Map<String, Object>>() {});
        return CareerItemDetailsVo.fromValidated(type, fields)
                .convertToCareerItemEntity(row.getLong("career_item_id"));
    }

    /** 목록 조회 SQL이 반환한 표시 필드를 자료 요약 VO로 변환한다. */
    private CareerItemSummaryVo convertToCareerItemSummaryVo(ResultSet row, int rowNumber) throws SQLException {
        return new CareerItemSummaryVo(row.getLong("career_item_id"),
                CareerItemType.valueOf(row.getString("career_item_type")), row.getString("display_name"),
                row.getString("history_type"), row.getString("start_month"), row.getString("issued_on"));
    }
}
