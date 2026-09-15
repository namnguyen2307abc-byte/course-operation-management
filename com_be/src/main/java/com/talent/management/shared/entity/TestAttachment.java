package com.talent.management.shared.entity;

import com.talent.management.shared.enums.TestMediaType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_test_id", nullable = false)
    private PlacementTest placementTest;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 1000)
    private String fileUrl; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TestMediaType mediaType; 

    private Integer durationSeconds;

    @Column(length = 500)
    private String description;

    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
