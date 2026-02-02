package com.impostor.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint con SockJS (recomendado para compatibilidad)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")

                .withSockJS();

        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(
            org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor = org.springframework.messaging.support.MessageHeaderAccessor
                        .getAccessor(message, org.springframework.messaging.simp.stomp.StompHeaderAccessor.class);

                if (accessor != null && org.springframework.messaging.simp.stomp.StompCommand.CONNECT
                        .equals(accessor.getCommand())) {
                    Object raw = accessor.getNativeHeader("playerId");
                    String playerId = null;
                    if (raw instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) raw;
                        if (!list.isEmpty()) {
                            playerId = list.get(0).toString();
                        }
                    }

                    if (playerId != null) {
                        accessor.setUser(new StompPrincipal(playerId));
                    }
                }
                return message;
            }
        });
    }
}
