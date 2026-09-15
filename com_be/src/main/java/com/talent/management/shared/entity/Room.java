package com.talent.management.shared.entity;

import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 50)
    private String roomCode;

    @Column(nullable = false, length = 150)
    private String roomName;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;

    @Column(length = 500)
    private String description;
}
