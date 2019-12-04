# SpringRawWebSocketSecurity
Example for Spring WebSocket with authentication

# Why I Need This?
I need this beacuse I don't want to use STOMP implementation in Spring WebSocket so I need to create my own WebSocketHandshakeHandler for WebSocket authentication. I'am using that class with JWT but you can use any authentication mechanism. You have to give a class and method thats returns the Principal object in Spring Security.
