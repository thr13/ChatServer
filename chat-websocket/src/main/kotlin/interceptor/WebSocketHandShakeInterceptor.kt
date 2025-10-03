package interceptor

import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.lang.Exception

//웹소켓에 대한 config 작업할때, 인터셉터를 등록을 하는데 이때 파라미터로 넘겨지는 값이 해당 핸드쉐이크 인터셉터를 만족하는 클래스로 생성해줘야함
@Component
class WebSocketHandShakeInterceptor : HandshakeInterceptor {

    private val logger = LoggerFactory.getLogger(WebSocketHandShakeInterceptor::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        return try {
            val uri = request.uri
            val query = uri.query

            if (query != null) {
                val param = parseQuery(query)
                val userId = param["userId"]?.toLongOrNull()

                if (userId != null) {
                    attributes["userId"] = userId
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
            false
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        if (exception != null) {
            logger.error("WebSocket HandshakeInterceptor exception", exception)
        } else {
            logger.info("WebSocket HandshakeInterceptor")
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        //쿼리 값만 출력
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else null
            }
            .toMap()
    }

}