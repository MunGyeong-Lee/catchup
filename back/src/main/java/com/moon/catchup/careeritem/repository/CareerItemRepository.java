package com.moon.catchup.careeritem.repository;

import java.util.List;

import com.moon.catchup.careeritem.domain.CareerItem;
import com.moon.catchup.careeritem.domain.CareerItemDetails;
import com.moon.catchup.careeritem.domain.CareerItemSummary;
import com.moon.catchup.careeritem.domain.CareerItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** 자료의 등록과 목록 조회 쿼리를 PostgreSQL에서 실행한다. */
@Repository
@RequiredArgsConstructor
public class CareerItemRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** 검증된 자료 입력을 PostgreSQL에 저장한다. */
    public CareerItem insert(CareerItemDetails details) {
        Long id = this.jdbc.queryForObject("""
                INSERT INTO career_items (career_item_type, details)
                VALUES (?, CAST(? AS jsonb))
                RETURNING career_item_id
                """, Long.class, details.getType().name(), this.mapper.writeValueAsString(details.getFields()));
        return new CareerItem(id, details);
    }

    /** 최근 등록 순으로 자료 유형별 표시 정보를 조회한다. */
    public List<CareerItemSummary> list() {
        return this.jdbc.query("""
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
                """, (row, index) -> new CareerItemSummary(row.getLong("career_item_id"),
                CareerItemType.valueOf(row.getString("career_item_type")), row.getString("display_name"),
                row.getString("history_type"), row.getString("start_month"), row.getString("issued_on")));
    }
}
