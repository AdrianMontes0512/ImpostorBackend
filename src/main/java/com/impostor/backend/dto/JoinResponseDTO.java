package com.impostor.backend.dto;

import com.impostor.backend.model.Player;
import com.impostor.backend.model.Room;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinResponseDTO {
    private Player player;
    private Room room;
}
