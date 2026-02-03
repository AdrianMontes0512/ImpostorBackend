package com.impostor.backend.service;

import com.impostor.backend.dto.PrivatePlayerStateDTO;
import com.impostor.backend.model.GameState;
import com.impostor.backend.model.Player;
import com.impostor.backend.model.Role;
import com.impostor.backend.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstSpeakerLogicTest {

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    private Room room;
    private Player p1, p2, p3;

    @BeforeEach
    void setUp() {
        room = new Room("TEST");
        p1 = new Player("1", "User1", Role.PLAYER);
        p2 = new Player("2", "User2", Role.PLAYER);
        p3 = new Player("3", "User3", Role.PLAYER);

        room.getPlayers().add(p1);
        room.getPlayers().add(p2);
        room.getPlayers().add(p3);

        when(roomService.getRoom("TEST")).thenReturn(room);
    }

    @Test
    void testFirstSpeakerRotationAndCarryOver() {
        // --- Game 1 ---

        // Start Game
        gameService.startGame("TEST");

        // Simulate Category & Word Input to reach Voting (Round 1)
        room.setGameState(GameState.WORD_INPUT);
        // Force submit words (mocking the internal logic or just setting state directly
        // for test speed)
        // Ideally we call startGame -> submitCategory -> submitWord
        // But let's just cheat state to test the specific logic

        // Manually trigger calling 'submitWord' logic or equivalent?
        // GameService.submitWord calls determineFirstSpeaker.
        // Let's use submitWord for p1, p2, p3.

        room.setGameState(GameState.WORD_INPUT);
        // We need Role to be PLAYER/IMPOSTOR. startGame sets it.
        // Let's assume startGame worked.
        // We mocked players, but startGame overwrites roles.
        // We can manually set roles to effectively skip random assignment
        p1.setRole(Role.PLAYER);
        p2.setRole(Role.PLAYER);
        p3.setRole(Role.IMPOSTOR);
        room.setImpostorId("3");

        // Submit Words for Innocent players (P1, P2)
        gameService.submitWord("TEST", "1", "word1");
        gameService.submitWord("TEST", "2", "word2"); // This should trigger Round 1 start

        assertEquals(GameState.VOTING, room.getGameState());
        assertEquals(1, room.getCurrentRound());
        assertNotNull(room.getFirstSpeakerId());
        System.out.println("Round 1 First Speaker: " + room.getFirstSpeakerId());
        String round1Speaker = room.getFirstSpeakerId();

        // --- Round 2 (Rotation) ---
        // Simulate voting result that proceeds to next round
        // We need to manipulate votes.
        // Let's assume no one ejected (Tie).
        // Calling vote() enough times calls calculateResults()

        // P1 votes P2, P2 votes P3, P3 votes P1 (Cycle -> Tie)
        gameService.vote("TEST", "1", "2");
        gameService.vote("TEST", "2", "3");
        gameService.vote("TEST", "3", "1");

        assertEquals(2, room.getCurrentRound());
        assertNotNull(room.getFirstSpeakerId());
        assertNotEquals(round1Speaker, room.getFirstSpeakerId());
        System.out.println("Round 2 First Speaker: " + room.getFirstSpeakerId());
        String round2Speaker = room.getFirstSpeakerId();

        // --- Game End ---
        // Let's finish the game. Max rounds = 3 usually.
        // Let's just force finish by getting to max rounds?
        // Or simpler: Mock verify finishGame saves the speaker.

        // Let's play round 2 votes -> Round 3
        gameService.vote("TEST", "1", "2");
        gameService.vote("TEST", "2", "3");
        gameService.vote("TEST", "3", "1");
        assertEquals(3, room.getCurrentRound());
        String round3Speaker = room.getFirstSpeakerId();
        System.out.println("Round 3 First Speaker: " + round3Speaker);

        // Round 3 votes -> Finish Game (Max rounds reached if max=3)
        room.setMaxRounds(3);
        gameService.vote("TEST", "1", "2");
        gameService.vote("TEST", "2", "3");
        gameService.vote("TEST", "3", "1");

        assertEquals(GameState.FINISHED, room.getGameState());
        // Verify carry-over
        assertEquals(round3Speaker, room.getPreviousGameLastFirstSpeakerId());

        // --- Game 2 ---
        // Start Game 2
        gameService.startGame("TEST");
        // Check reset didn't clear it
        assertEquals(round3Speaker, room.getPreviousGameLastFirstSpeakerId());

        // Reach Round 1 of Game 2
        room.setGameState(GameState.WORD_INPUT);
        p1.setRole(Role.PLAYER);
        p2.setRole(Role.PLAYER);
        p3.setRole(Role.IMPOSTOR);
        room.getWordSuggestions().clear();

        gameService.submitWord("TEST", "1", "wordNew");
        gameService.submitWord("TEST", "2", "wordNew");

        assertEquals(GameState.VOTING, room.getGameState());
        assertEquals(1, room.getCurrentRound());
        // The first speaker of Game 2 Round 1 MUST be the speaker from Game 1 Round 3
        assertEquals(round3Speaker, room.getFirstSpeakerId());
    }
}
