import handler.ChatWebSocketHandler
import interceptor.WebSocketHandShakeInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig (
    private val chatWebSocketHandler: ChatWebSocketHandler,
    private val webSocketHandShakeInterceptor: WebSocketHandShakeInterceptor
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
            .addInterceptors(webSocketHandShakeInterceptor)
            .setAllowedOrigins("*") //production => 도메인 고려
    }
}