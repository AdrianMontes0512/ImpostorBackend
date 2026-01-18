package com.impostor.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrivatePlayerStateDTO {
    private String role;
    private String category;
    private String secretWord;
    private String message;
}
