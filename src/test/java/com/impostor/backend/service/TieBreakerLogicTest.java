package com.impostor.backend.service;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TieBreakerLogicTest {

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    private Room room;
    private Player p1, p2, p3, p4;

    @BeforeEach
    void setUp() {
        room = new Room("TEST");
        p1 = new Player("1", "User1", Role.PLAYER);
        p2 = new Player("2", "User2", Role.PLAYER);
        p3 = new Player("3", "User3", Role.PLAYER);
        p4 = new Player("4", "User4", Role.PLAYER);

        room.getPlayers().add(p1);
        room.getPlayers().add(p2);
        room.getPlayers().add(p3);
        room.getPlayers().add(p4);

        room.setGameState(GameState.VOTING);
        when(roomService.getRoom("TEST")).thenReturn(room);
    }

    @Test
    void testTieBreakerTrigger() {
        // Vote: P1->P2, P2->P1, P3->P2, P4->P1
        // Tie between P1 and P2 (2 votes each)

        gameService.vote("TEST", "1", "2");
        gameService.vote("TEST", "2", "1");
        gameService.vote("TEST", "3", "2");
        gameService.vote("TEST", "4", "1");

        // Should trigger tie breaker
        assertTrue(room.isTieBreaker());
        assertEquals(2, room.getTiedPlayerIds().size());
        assertTrue(room.getTiedPlayerIds().contains("1"));
        assertTrue(room.getTiedPlayerIds().contains("2"));
        assertEquals(GameState.VOTING, room.getGameState());
        assertTrue(room.getVotes().isEmpty());
    }

    @Test
    void testRestrictedVotingDuringTieBreaker() {
        room.setTieBreaker(true);
        room.getTiedPlayerIds().add("1");
        room.getTiedPlayerIds().add("2");

        // Try to vote for P3 (not tied) -> Should be ignored
        gameService.vote("TEST", "4", "3");
        assertFalse(room.getVotes().containsKey("4"));

        // Vote for P1 (tied) -> Should be accepted
        gameService.vote("TEST", "4", "1");
        assertTrue(room.getVotes().containsKey("4"));
    }

    @Test
    void testTieBreakerResolution() {
        room.setTieBreaker(true);
        room.getTiedPlayerIds().add("1");
        room.getTiedPlayerIds().add("2");

        // Mock votes resolving the tie: 3 votes for P1, 1 vote for P2
        gameService.vote("TEST", "1", "2");
        gameService.vote("TEST", "2", "1");
        gameService.vote("TEST", "3", "1");
        gameService.vote("TEST", "4", "1");

        // P1 ejected
        assertFalse(room.isTieBreaker());
        assertEquals(Role.SPECTATOR, p1.getRole());
    }

    @Test
    void testSecondTieRandomEjection() {
        room.setTieBreaker(true);
        room.getTiedPlayerIds().add("1");
        room.getTiedPlayerIds().add("2");

        // Tie again: 2 votes P1, 2 votes P2
        gameService.vote("TEST", "1", "2");
        gameService.vote("TEST", "2", "1");
        gameService.vote("TEST", "3", "2");
        gameService.vote("TEST", "4", "1");

        // Random ejection logic should occur, tie breaker cleared
        assertFalse(room.isTieBreaker());
        // One of them should be spectator
        assertTrue(p1.getRole() == Role.SPECTATOR || p2.getRole() == Role.SPECTATOR);
    }
}
