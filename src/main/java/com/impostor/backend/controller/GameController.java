package com.impostor.backend.controller;

import com.impostor.backend.dto.JoinRoomDTO;
import com.impostor.backend.dto.VoteDTO;
import com.impostor.backend.model.Player;
import com.impostor.backend.model.Room;
import com.impostor.backend.service.GameService;
import com.impostor.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final RoomService roomService;
    private final GameService gameService;

    @PostMapping("/create")
    public ResponseEntity<Room> createRoom(@RequestBody com.impostor.backend.dto.CreateRoomDTO createRoomDTO) {
        return ResponseEntity.ok(roomService.createRoom(createRoomDTO.getUsername(), createRoomDTO.getMaxRounds()));
    }

    @PostMapping("/join/{roomCode}")
    public ResponseEntity<com.impostor.backend.dto.JoinResponseDTO> joinRoom(@PathVariable String roomCode,
            @RequestBody JoinRoomDTO joinRoomDTO) {
        String sessionId = java.util.UUID.randomUUID().toString();
        Player player = roomService.joinRoom(roomCode, joinRoomDTO.getUsername(), sessionId);

        Room room = roomService.getRoom(roomCode);
        gameService.broadcastRoomUpdate(room, player.getUsername() + " joined.");

        return ResponseEntity.ok(new com.impostor.backend.dto.JoinResponseDTO(player, room));
    }

    @MessageMapping("/room/{roomCode}/start")
    public void startGame(@DestinationVariable String roomCode) {
        gameService.startGame(roomCode);
    }

    @MessageMapping("/room/{roomCode}/category")
    public void submitCategory(@DestinationVariable String roomCode, @Payload com.impostor.backend.dto.InputDTO input) {
        gameService.submitCategory(roomCode, input.getPlayerId(), input.getValue());
    }

    @MessageMapping("/room/{roomCode}/word")
    public void submitWord(@DestinationVariable String roomCode, @Payload com.impostor.backend.dto.InputDTO input) {
        gameService.submitWord(roomCode, input.getPlayerId(), input.getValue());
    }

    @MessageMapping("/room/{roomCode}/vote")
    public void vote(@DestinationVariable String roomCode, @Payload VoteDTO voteDTO) {
        gameService.vote(roomCode, voteDTO.getVoterId(), voteDTO.getVotedPlayerId());
    }

    @MessageMapping("/room/{roomCode}/reset")
    public void resetGame(@DestinationVariable String roomCode) {
        gameService.resetGame(roomCode);
    }
}
