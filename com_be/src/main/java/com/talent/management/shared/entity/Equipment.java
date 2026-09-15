package com.talent.management.shared.entity;

import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EquipmentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EquipmentStatus status = EquipmentStatus.GOOD;

    @Column(length = 100)
    private String serialNumber;

    @Column(length = 500)
    private String description;
}
