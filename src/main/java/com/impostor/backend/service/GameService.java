package com.impostor.backend.service;

import com.impostor.backend.dto.PrivatePlayerStateDTO;
import com.impostor.backend.dto.RoomStatusDTO;
import com.impostor.backend.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public void startGame(String roomCode) {
        Room room = roomService.getRoom(roomCode);
        if (room == null || room.getPlayers().size() < 3) {
            // Minimum 3 players needed
            return;
        }

        room.reset();
        room.setGameState(GameState.ASSIGN_ROLES);
        room.setCurrentRound(0);

        // Assign Impostor
        List<Player> players = room.getPlayers();
        Random random = new Random();
        Player impostor = players.get(random.nextInt(players.size()));
        impostor.setRole(Role.IMPOSTOR);
        // Duplicate assignment removed
        room.setImpostorId(impostor.getId());
        room.setImpostorName(impostor.getUsername());

        for (Player p : players) {
            if (!p.getId().equals(impostor.getId())) {
                p.setRole(Role.PLAYER);
            }
            // Notify each player of their role
            PrivatePlayerStateDTO dto = new PrivatePlayerStateDTO(
                    p.getRole().toString(),
                    null,
                    null,
                    "Role Assigned: " + p.getRole());
            messagingTemplate.convertAndSendToUser(p.getId(), "/queue/game", dto);
        }

        // Move to Category Input
        room.setGameState(GameState.CATEGORY_INPUT);
        broadcastRoomUpdate(room, "Waiting for Category suggestions...");
    }

    public void submitCategory(String roomCode, String playerId, String category) {
        Room room = roomService.getRoom(roomCode);
        if (room == null || room.getGameState() != GameState.CATEGORY_INPUT)
            return;

        Player player = getPlayer(room, playerId);
        if (player == null)
            return;

        room.getCategorySuggestions().put(playerId, category);

        System.out.println("DEBUG: Player " + player.getUsername() + " submitted category: " + category);
        System.out.println(
                "DEBUG: Suggestions: " + room.getCategorySuggestions().size() + " / " + room.getPlayers().size());

        if (room.getCategorySuggestions().size() >= room.getPlayers().size()) {
            System.out.println("DEBUG: All categories received. advancing state.");
            // Everyone submitted
            List<String> values = new ArrayList<>(room.getCategorySuggestions().values());
            String selected = values.get(new Random().nextInt(values.size()));
            room.setSelectedCategory(selected);

            room.setGameState(GameState.WORD_INPUT);
            broadcastRoomUpdate(room, "Category Selected: " + selected + ". Waiting for words...");

            // Notify everyone of the category
            room.getPlayers().forEach(p -> {
                PrivatePlayerStateDTO dto = new PrivatePlayerStateDTO(
                        p.getRole().toString(),
                        selected,
                        null,
                        "Category is: " + selected);
                messagingTemplate.convertAndSendToUser(p.getId(), "/queue/game", dto);
            });
        }
    }

    public void submitWord(String roomCode, String playerId, String word) {
        Room room = roomService.getRoom(roomCode);
        if (room == null || room.getGameState() != GameState.WORD_INPUT)
            return;

        Player player = getPlayer(room, playerId);
        if (player == null || player.getRole() == Role.IMPOSTOR)
            return;

        room.getWordSuggestions().put(playerId, word);

        long innocentCount = room.getPlayers().stream().filter(p -> p.getRole() == Role.PLAYER).count();
        if (room.getWordSuggestions().size() >= innocentCount) {
            List<String> values = new ArrayList<>(room.getWordSuggestions().values());
            String selected = values.get(new Random().nextInt(values.size()));
            room.setSelectedWord(selected);

            room.setGameState(GameState.VOTING);
            room.setCurrentRound(1);
            broadcastRoomUpdate(room, "Word Selected! Round 1 Begins.");

            // Notify players of the word (Impostor gets ???)
            room.getPlayers().forEach(p -> {
                String wordToSend = (p.getRole() == Role.IMPOSTOR) ? "???" : selected;
                PrivatePlayerStateDTO dto = new PrivatePlayerStateDTO(
                        p.getRole().toString(),
                        room.getSelectedCategory(),
                        wordToSend,
                        "Game Started!");
                messagingTemplate.convertAndSendToUser(p.getId(), "/queue/game", dto);
            });
        }
    }

    public void vote(String roomCode, String voterId, String votedPlayerId) {
        Room room = roomService.getRoom(roomCode);
        if (room == null || !isVotingState(room.getGameState()))
            return;

        Player voter = getPlayer(room, voterId);
        if (voter == null || voter.getRole() == Role.SPECTATOR)
            return; // Spectators can't vote

        room.getVotes().put(voterId, votedPlayerId);

        long activePlayers = room.getPlayers().stream()
                .filter(p -> p.getRole() != Role.SPECTATOR)
                .count();

        if (room.getVotes().size() >= activePlayers) {
            calculateResults(room);
        }
    }

    private void calculateResults(Room room) {
        Map<String, Long> voteCounts = room.getVotes().values().stream()
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        String ejectedId = voteCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        room.getVotes().clear();

        if (ejectedId != null) {
            Player ejected = getPlayer(room, ejectedId);
            if (ejected != null) {
                if (ejected.getRole() == Role.IMPOSTOR) {
                    finishGame(room, "Impostor Ejected! Players Win!");
                    return;
                } else {
                    ejected.setRole(Role.SPECTATOR);
                    broadcastRoomUpdate(room, ejected.getUsername() + " was NOT the Impostor.");
                }
            }
        } else {
            broadcastRoomUpdate(room, "No one ejected (Tie).");
        }

        // Check if Impostor wins by 1v1
        long activePlayers = room.getPlayers().stream()
                .filter(p -> p.getRole() != Role.SPECTATOR)
                .count();

        boolean impostorAlive = room.getPlayers().stream()
                .anyMatch(p -> p.getRole() == Role.IMPOSTOR);

        if (activePlayers <= 2 && impostorAlive) {
            finishGame(room, "Impostor Wins! (1v1 Situation)");
            return;
        }

        // Next Round Logic

        GameState nextState = getNextRound(room);
        if (nextState == GameState.FINISHED) {
            finishGame(room, "Impostor Survived! Impostor Wins!");
        } else {
            room.setGameState(nextState);
            broadcastRoomUpdate(room, "Starting Round " + room.getCurrentRound());
        }
    }

    private void finishGame(Room room, String message) {
        room.setGameState(GameState.FINISHED);
        broadcastRoomUpdate(room, message);
    }

    private GameState getNextRound(Room room) {
        int nextRound = room.getCurrentRound() + 1;
        if (nextRound > room.getMaxRounds()) {
            return GameState.FINISHED;
        }
        room.setCurrentRound(nextRound);
        return GameState.VOTING;
    }

    private boolean isVotingState(GameState state) {
        return state == GameState.VOTING;
    }

    private Player getPlayer(Room room, String playerId) {
        return room.getPlayers().stream().filter(p -> p.getId().equals(playerId)).findFirst().orElse(null);
    }

    public void resetGame(String roomCode) {
        Room room = roomService.getRoom(roomCode);
        if (room != null) {
            room.reset();
            broadcastRoomUpdate(room, "Game Reset to Lobby");
        }
    }

    public void broadcastRoomUpdate(Room room, String message) {
        RoomStatusDTO status = new RoomStatusDTO(
                room.getRoomCode(),
                room.getPlayers(),
                room.getGameState(),
                message,
                room.getCurrentRound(),
                room.getMaxRounds(),
                room.getGameState() == GameState.FINISHED ? room.getImpostorName() : null);
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), status);
    }
}
