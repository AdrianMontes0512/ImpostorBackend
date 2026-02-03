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

            determineFirstSpeaker(room);

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

        if (room.isTieBreaker() && !room.getTiedPlayerIds().contains(votedPlayerId)) {
            // Can only vote for tied players
            return;
        }

        room.getVotes().put(voterId, votedPlayerId);

        long activePlayers = room.getPlayers().stream()
                .filter(p -> p.getRole() != Role.SPECTATOR)
                .count();

        System.out.println("DEBUG: Vote received from " + voterId + " for " + votedPlayerId);
        System.out.println("DEBUG: Votes: " + room.getVotes().size() + "/" + activePlayers);

        if (room.getVotes().size() >= activePlayers) {
            System.out.println("DEBUG: All votes received. Calculating results...");
            try {
                calculateResults(room);
            } catch (Exception e) {
                System.err.println("ERROR in calculateResults: ");
                e.printStackTrace();
            }
        }
    }

    private void calculateResults(Room room) {
        Map<String, Long> voteCounts = room.getVotes().values().stream()
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        long maxVotes = voteCounts.values().stream().mapToLong(v -> v).max().orElse(0);
        System.out.println("DEBUG: Max votes: " + maxVotes);

        List<String> maxVoteIds = voteCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        System.out.println("DEBUG: Candidates with max votes: " + maxVoteIds);

        String ejectedId = null;

        if (maxVoteIds.size() > 1) {
            // Tie detected
            long activePlayers = room.getPlayers().stream()
                    .filter(p -> p.getRole() != Role.SPECTATOR)
                    .count();

            if (activePlayers <= 2) {
                // Special case: 1v1 Tie -> Impostor Wins (game over) or just continue?
                // Usually 1v1 tie means Impostor wins as they can't be voted out.
                // let's stick to standard logic.
            }

            if (!room.isTieBreaker()) {
                System.out.println("DEBUG: Triggering Tie-Breaker round.");
                // First tie -> Trigger Tie Breaker Round
                room.setTieBreaker(true);
                room.setTiedPlayerIds(maxVoteIds);
                room.getVotes().clear();

                // Construct names string
                String names = room.getPlayers().stream()
                        .filter(p -> maxVoteIds.contains(p.getId()))
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", "));

                System.out.println("DEBUG: Tie-breaker set. Broadcasting update.");
                broadcastRoomUpdate(room, "Empate! Votación de desempate entre: " + names);
                return; // Do NOT advance round, stay in VOTING
            } else {
                // Second tie (Tie Breaker) -> Random Ejection
                ejectedId = maxVoteIds.get(new Random().nextInt(maxVoteIds.size()));
                room.setTieBreaker(false);
                room.getTiedPlayerIds().clear();
                broadcastRoomUpdate(room, "Desempate fallido. Expulsión aleatoria.");
            }
        } else if (maxVoteIds.size() == 1) {
            ejectedId = maxVoteIds.get(0);
            // Ensure tie breaker state is cleared if resolved
            room.setTieBreaker(false);
            room.getTiedPlayerIds().clear();
        } else {
            // No votes?
            broadcastRoomUpdate(room, "Nadie votó (Tie).");
        }

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
            determineFirstSpeaker(room);
            broadcastRoomUpdate(room, "Starting Round " + room.getCurrentRound());
        }
    }

    private void finishGame(Room room, String message) {
        room.setPreviousGameLastFirstSpeakerId(room.getFirstSpeakerId());
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
                room.getGameState() == GameState.FINISHED ? room.getImpostorName() : null,
                room.getFirstSpeakerId(),
                room.isTieBreaker(),
                room.getTiedPlayerIds());
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), status);
    }

    private void determineFirstSpeaker(Room room) {
        List<Player> activePlayers = room.getPlayers().stream()
                .filter(p -> p.getRole() != Role.SPECTATOR)
                .collect(Collectors.toList());

        if (activePlayers.isEmpty())
            return;

        if (room.getCurrentRound() == 1) {
            String targetId = room.getPreviousGameLastFirstSpeakerId();
            boolean found = false;
            if (targetId != null) {
                for (Player p : activePlayers) {
                    if (p.getId().equals(targetId)) {
                        room.setFirstSpeakerId(targetId);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                room.setFirstSpeakerId(activePlayers.get(new Random().nextInt(activePlayers.size())).getId());
            }
        } else {
            String currentSpeakerId = room.getFirstSpeakerId();
            int index = -1;
            for (int i = 0; i < activePlayers.size(); i++) {
                if (activePlayers.get(i).getId().equals(currentSpeakerId)) {
                    index = i;
                    break;
                }
            }

            // If current speaker not found (e.g. eliminated), index is -1, nextIndex is 0
            // (first player)
            int nextIndex = (index + 1) % activePlayers.size();
            room.setFirstSpeakerId(activePlayers.get(nextIndex).getId());
        }
    }
}
