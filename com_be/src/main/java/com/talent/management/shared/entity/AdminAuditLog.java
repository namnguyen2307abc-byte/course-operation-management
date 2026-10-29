package com.talent.management.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Immutable administration history; never stores credentials or tokens. */
@Entity
@Table(name = "admin_audit_logs")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long actorId;
    @Column(nullable = false, length = 100)
    private String actorUsername;
    @Column(nullable = false)
    private Long targetId;
    @Column(nullable = false, length = 100)
    private String targetUsername;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(nullable = false, length = 2000)
    private String details;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
