package com.impostor.backend.service;

import com.impostor.backend.model.Player;
import com.impostor.backend.model.Room;
import com.impostor.backend.model.WordPool;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(String username, int maxRounds) {
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Room room = new Room(roomCode);
        room.setMaxRounds(maxRounds > 0 ? maxRounds : 3);

        // Add creator as first player
        String sessionId = UUID.randomUUID().toString();
        Player creator = new Player(sessionId, username, null);
        room.getPlayers().add(creator);

        rooms.put(roomCode, room);
        return room;
    }

    public Room getRoom(String roomCode) {
        return rooms.get(roomCode);
    }

    public Player joinRoom(String roomCode, String username, String sessionId) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Check if player already exists
        for (Player p : room.getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(username)) {
                p.setId(sessionId); // Update session ID for reconnect
                return p;
            }
        }

        Player player = new Player(sessionId, username, null);
        room.getPlayers().add(player);
        return player;
    }
}
