package com.impostor.backend.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Room {
    private String roomCode;
    private List<Player> players = new ArrayList<>();
    private List<WordPool> wordPools = new ArrayList<>();
    private GameState gameState = GameState.LOBBY;
    
    private String impostorId;
    
    // Suggestions: Map<PlayerId, Suggestion>
    private Map<String, String> categorySuggestions = new ConcurrentHashMap<>();
    private Map<String, String> wordSuggestions = new ConcurrentHashMap<>();
    
    // Selected values
    private String selectedCategory;
    private String selectedWord;
    
    // Map<VoterID, VotedPlayerID>
    private Map<String, String> votes = new ConcurrentHashMap<>();

    public Room(String roomCode) {
        this.roomCode = roomCode;
    }
    
    public void reset() {
        this.gameState = GameState.LOBBY;
        this.impostorId = null;
        this.categorySuggestions.clear();
        this.wordSuggestions.clear();
        this.selectedCategory = null;
        this.selectedWord = null;
        this.votes.clear();
        this.players.forEach(p -> p.setRole(null));
    }
}
