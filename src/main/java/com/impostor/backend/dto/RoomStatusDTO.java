package com.impostor.backend.dto;

import com.impostor.backend.model.GameState;
import com.impostor.backend.model.Player;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomStatusDTO {
    private String roomCode;
    private List<Player> players;
    private GameState gameState;
    private String message;
    private int currentRound;
    private int maxRounds;
    private String impostorName;
}
