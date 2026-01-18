# Documentación de Integración Backend - El Impostor

Esta guía detalla cómo integrar el cliente Frontend con el servidor de juego "El Impostor" utilizando WebSockets y STOMP.

## 1. Configuración de Conexión

El servidor utiliza **SockJS** y **STOMP** para la comunicación en tiempo real.

*   **Endpoint WebSocket:** `URL_DEL_BACKEND/ws`
*   **Librerías recomendadas:** `sockjs-client`, `@stomp/stompjs`

### Ejemplo de conexión (JavaScript/TypeScript)

```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

const socket = new SockJS('URL_DEL_BACKEND/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
    console.log('Conectado: ' + frame);
});
```

---

## 2. Canales de Suscripción (Escucha)

Debes suscribirte a estos canales para recibir actualizaciones del juego.

### A. Estado Global de la Sala (Público)
Recibe actualizaciones sobre jugadores conectados, estado del juego y resultados de votaciones.

*   **Canal:** `/topic/room/{roomCode}`
*   **Payload (RoomStatusDTO):**

```json
{
  "roomCode": "ABCD12",
  "players": [
    { "id": "uuid-1", "username": "Player1", "role": "PLAYER" },
    { "id": "uuid-2", "username": "Player2", "role": "SPECTATOR" }
  ],
  "gameState": "LOBBY", // Ver lista completa de estados abajo
  "message": "Player1 joined."
}
```

### B. Estado Privado del Jugador (Privado)
Recibe información secreta que **solo este usuario** debe ver (su rol, la palabra secreta).

*   **Canal:** `/user/queue/game`
*   **Payload (PrivatePlayerStateDTO):**

```json
{
  "role": "IMPOSTOR", // o "PLAYER"
  "category": "Frutas", // Visible en fases de juego
  "secretWord": "???", // "???" si es Impostor, palabra real si es Player
  "message": "Role Assigned: IMPOSTOR"
}
```

---

## 3. Endpoints de Envío (Acciones)

Para interactuar con el juego, envía mensajes JSON a los siguientes destinos (`/app/...`).

### 3.1 Crear Sala (HTTP REST)
*   **Método:** `POST`
*   **URL:** `URL_DEL_BACKEND/api/game/create`
*   **Body:**
    ```json
    { "username": "MiNombre" }
    ```
*   **Respuesta:** Objeto `Room` con el `roomCode` y la lista de jugadores (incluyéndote). **Nota:** El creador se une automáticamente.

### 3.2 Unirse a Sala (HTTP REST)
*   **Método:** `POST`
*   **URL:** `URL_DEL_BACKEND/api/game/join/{roomCode}`
*   **Body:**
    ```json
    { "username": "MiNombre" }
    ```
*   **Respuesta:** Objeto `Player` con tu `id` (Guárdalo, lo necesitas para votar/enviar).

### 3.3 Iniciar Juego (WebSocket)
*   **Destino:** `/app/room/{roomCode}/start`
*   **Body:** (Vacío)

### 3.4 Enviar Categoría (WebSocket)
*   **Fase:** `CATEGORY_INPUT`
*   **Destino:** `/app/room/{roomCode}/category`
*   **Body:**
    ```json
    {
      "playerId": "tu-uuid-aqui",
      "value": "Animales"
    }
    ```

### 3.5 Enviar Palabra (WebSocket)
*   **Fase:** `WORD_INPUT`
*   **Destino:** `/app/room/{roomCode}/word`
*   **Body:**
    ```json
    {
      "playerId": "tu-uuid-aqui",
      "value": "Elefante"
    }
    ```

### 3.6 Votar (WebSocket)
*   **Fase:** `ROUND_1`, `ROUND_2`, `ROUND_3`
*   **Destino:** `/app/room/{roomCode}/vote`
*   **Body:**
    ```json
    {
      "voterId": "tu-uuid-aqui",
      "votedPlayerId": "uuid-del-sospechoso"
    }
    ```

### 3.7 Resetear Juego (WebSocket)
*   **Fase:** `FINISHED`
*   **Destino:** `/app/room/{roomCode}/reset`
*   **Body:** (Vacío)

---

## 4. Flujo de Estados del Juego (GameState)

El campo `gameState` en el canal público indicará la fase actual:

1.  `LOBBY`: Esperando jugadores.
2.  `ASSIGN_ROLES`: El servidor está repartiendo roles (transitorio).
3.  `CATEGORY_INPUT`: Jugadores (no impostor) envían sugerencias de categoría.
4.  `WORD_INPUT`: Jugadores (no impostor) envían palabra secreta basada en la categoría elegida.
5.  `ROUND_1`: Primera ronda de votación.
6.  `ROUND_2`: Segunda ronda (si nadie ganó).
7.  `ROUND_3`: Ronda final.
8.  `FINISHED`: Juego terminado.

## 5. Condiciones de Victoria

El juego termina cuando `gameState` cambia a `FINISHED`. Debes leer el campo `message` del último payload para saber quién ganó, o inferirlo:

*   **Ganan Jugadores:** Si el Impostor es expulsado en cualquier ronda.
    *   *Mensaje:* "Impostor Ejected! Players Win!"
*   **Gana Impostor:** Si sobrevive a la `ROUND_3`.
    *   *Mensaje:* "Impostor Survived! Impostor Wins!"

---
**Nota:** Los IDs de los jugadores son UUIDs generados por el servidor al unirse. Úsalos para identificar a quién votar.
