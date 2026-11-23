package com.moon.catchup.domain.template;

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

/** 템플릿의 유형, 제목, 편집 구조를 DB에 매핑한다. */
@Entity
@Table(name = "templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private TemplateDocumentType documentType;

    @Column(name = "title", nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structure", nullable = false)
    private String structureJson;

    /**
     * 저장할 요청 값을 템플릿 엔티티에 담는다.
     *
     * @param template 저장할 문서 유형, 제목, JSON 객체
     */
    public TemplateEntity(TemplateVo template) {
        this.documentType = template.getDocumentType();
        this.title = template.getTitle();
        this.structureJson = template.getStructureJson();
    }
}
