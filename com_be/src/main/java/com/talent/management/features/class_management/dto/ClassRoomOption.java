package com.talent.management.features.class_management.dto;

import com.talent.management.shared.enums.RoomType;

public record ClassRoomOption(Long id, String roomCode, String roomName, Integer capacity, RoomType roomType) {
}
