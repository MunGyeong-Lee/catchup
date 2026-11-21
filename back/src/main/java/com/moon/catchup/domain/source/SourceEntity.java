package com.moon.catchup.domain.source;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.moon.catchup.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소스의 식별자, 유형과 {@code sources.details} JSONB를 DB에 매핑한다.
 *
 * <p>생성 시각은 PostgreSQL의 {@code timestamptz DEFAULT CURRENT_TIMESTAMP}로 정한다.
 * 등록·수정 입력에는 생성 시각을 사용하지 않는다.
 */
@Entity
@Table(name = "sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType type;

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * 검증된 상세 입력으로 새 소스를 만든다.
     *
     * @param details 유형별 상세 입력
     */
    public SourceEntity(SourceDetailsVo details) {
        updateDetails(details);
    }

    /**
     * 저장할 유형, 이름, 상세 입력이 달라졌으면 교체한다.
     *
     * @param details 유형별 상세 입력
     * @return 저장할 소스 필드가 바뀌었으면 true
     */
    public boolean updateDetails(SourceDetailsVo details) {
        Map<String, Object> storedFields = details.toStoredFields();
        if (this.type == details.getType() && Objects.equals(this.name, details.getName())
                && Objects.equals(this.details, storedFields)) {
            return false;
        }
        this.type = details.getType();
        this.name = details.getName();
        this.details = storedFields;
        return true;
    }
}
