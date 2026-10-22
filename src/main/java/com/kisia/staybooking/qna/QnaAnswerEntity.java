package com.kisia.staybooking.qna;

import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "qna_answers", uniqueConstraints =
    @UniqueConstraint(name = "uk_qna_answers_qna", columnNames = "qna_id"))
public class QnaAnswerEntity extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id") private Long answerId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qna_id", nullable = false, foreignKey = @ForeignKey(name = "fk_qna_answers_qna"))
    private QnaEntity qna;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, foreignKey = @ForeignKey(name = "fk_qna_answers_admin"))
    private UserEntity admin;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;

    protected QnaAnswerEntity() { }
    public QnaAnswerEntity(QnaEntity qna, UserEntity admin, String content) {
        this.qna = qna;
        this.admin = admin;
        this.content = content;
    }
    public Long getAnswerId() { return answerId; }
    public UserEntity getAdmin() { return admin; }
    public String getContent() { return content; }
    public void update(UserEntity admin, String content) {
        this.admin = admin;
        this.content = content;
    }
}
