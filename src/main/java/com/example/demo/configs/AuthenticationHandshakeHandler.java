package com.example.demo.configs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.Lifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.support.AbstractHandshakeHandler;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.function.Supplier;

public class AuthenticationHandshakeHandler implements HandshakeHandler, Lifecycle {
    private static final boolean jettyWsPresent;
    private static final boolean tomcatWsPresent;
    private static final boolean undertowWsPresent;
    private static final boolean glassfishWsPresent;
    private static final boolean weblogicWsPresent;
    private static final boolean websphereWsPresent;
    protected final Logger logger;
    private final RequestUpgradeStrategy requestUpgradeStrategy;
    private final List<String> supportedProtocols;
    private volatile boolean running;
    private Object authenticatorObj;
    private String invokeMethodName;
    private Principal principal;

    public AuthenticationHandshakeHandler(){
        this(initRequestUpgradeStrategy());
    }

    public AuthenticationHandshakeHandler(Object authenticatorObj, String invokeMethodName) {
        this(initRequestUpgradeStrategy());
        this.authenticatorObj = authenticatorObj;
        this.invokeMethodName = invokeMethodName;
    }

    public AuthenticationHandshakeHandler(RequestUpgradeStrategy requestUpgradeStrategy) {
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.supportedProtocols = new ArrayList<>();
        this.running = false;
        Assert.notNull(requestUpgradeStrategy, "RequestUpgradeStrategy must not be null");
        this.requestUpgradeStrategy = requestUpgradeStrategy;
    }

    private static RequestUpgradeStrategy initRequestUpgradeStrategy() {
        String className;
        if (tomcatWsPresent) {
            className = "org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy";
        } else if (jettyWsPresent) {
            className = "org.springframework.web.socket.server.jetty.JettyRequestUpgradeStrategy";
        } else if (undertowWsPresent) {
            className = "org.springframework.web.socket.server.standard.UndertowRequestUpgradeStrategy";
        } else if (glassfishWsPresent) {
            className = "org.springframework.web.socket.server.standard.GlassFishRequestUpgradeStrategy";
        } else if (weblogicWsPresent) {
            className = "org.springframework.web.socket.server.standard.WebLogicRequestUpgradeStrategy";
        } else {
            if (!websphereWsPresent) {
                throw new IllegalStateException("No suitable default RequestUpgradeStrategy found");
            }

            className = "org.springframework.web.socket.server.standard.WebSphereRequestUpgradeStrategy";
        }

        try {
            Class<?> clazz = ClassUtils.forName(className, AbstractHandshakeHandler.class.getClassLoader());
            return (RequestUpgradeStrategy) ReflectionUtils.accessibleConstructor(clazz, new Class[0]).newInstance();
        } catch (Throwable var2) {
            throw new IllegalStateException("Failed to instantiate RequestUpgradeStrategy: " + className, var2);
        }
    }

    public RequestUpgradeStrategy getRequestUpgradeStrategy() {
        return this.requestUpgradeStrategy;
    }

    public void setSupportedProtocols(String... protocols) {
        this.supportedProtocols.clear();
        String[] var2 = protocols;
        int var3 = protocols.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            String protocol = var2[var4];
            this.supportedProtocols.add(protocol.toLowerCase());
        }

    }

    public String[] getSupportedProtocols() {
        return StringUtils.toStringArray(this.supportedProtocols);
    }

    public void start() {
        if (!this.isRunning()) {
            this.running = true;
            this.doStart();
        }

    }

    protected void doStart() {
        if (this.requestUpgradeStrategy instanceof Lifecycle) {
            ((Lifecycle)this.requestUpgradeStrategy).start();
        }

    }

    public void stop() {
        if (this.isRunning()) {
            this.running = false;
            this.doStop();
        }

    }

    protected void doStop() {
        if (this.requestUpgradeStrategy instanceof Lifecycle) {
            ((Lifecycle)this.requestUpgradeStrategy).stop();
        }

    }

    public boolean isRunning() {
        return this.running;
    }

    public final boolean doHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws HandshakeFailureException {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders(request.getHeaders());
        if (this.logger.isTraceEnabled()) {
            this.logger.trace("Processing request " + request.getURI() + " with headers=" + headers);
        }

        String subProtocol;
        try {

            if (HttpMethod.GET != request.getMethod()) {
                response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
                response.getHeaders().setAllow(Collections.singleton(HttpMethod.GET));
                if (this.logger.isErrorEnabled()) {
                    this.logger.error("Handshake failed due to unexpected HTTP method: " + request.getMethod());
                }

                return false;
            }

            if (!"WebSocket".equalsIgnoreCase(headers.getUpgrade())) {
                this.handleInvalidUpgradeHeader(request, response);
                return false;
            }

            if (!headers.getConnection().contains("Upgrade") && !headers.getConnection().contains("upgrade")) {
                this.handleInvalidConnectHeader(request, response);
                return false;
            }

            if (!this.isWebSocketVersionSupported(headers)) {
                this.handleWebSocketVersionNotSupported(request, response);
                return false;
            }

            if (!this.isValidOrigin(request)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            subProtocol = headers.getSecWebSocketKey();
            if (subProtocol == null) {
                if (this.logger.isErrorEnabled()) {
                    this.logger.error("Missing \"Sec-WebSocket-Key\" header");
                }

                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
        } catch (IOException var11) {
            throw new HandshakeFailureException("Response update failed during upgrade to WebSocket: " + request.getURI(), var11);
        }

        subProtocol = this.selectProtocol(headers.getSecWebSocketProtocol(), wsHandler);
        List<WebSocketExtension> requested = headers.getSecWebSocketExtensions();
        List<WebSocketExtension> supported = this.requestUpgradeStrategy.getSupportedExtensions(request);
        List<WebSocketExtension> extensions = this.filterRequestedExtensions(request, requested, supported);
        Principal user = null;
        try {
            user = this.determineUser(request, wsHandler, attributes);
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            logger.error("User must logged in");
            e.printStackTrace();
            return false;
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("Upgrading to WebSocket, subProtocol=" + subProtocol + ", extensions=" + extensions);
        }

        this.requestUpgradeStrategy.upgrade(request, response, subProtocol, extensions, user, wsHandler, attributes);
        return true;
    }

    protected void handleInvalidUpgradeHeader(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
        if (this.logger.isErrorEnabled()) {
            this.logger.error("Handshake failed due to invalid Upgrade header: " + request.getHeaders().getUpgrade());
        }

        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getBody().write("Can \"Upgrade\" only to \"WebSocket\".".getBytes(StandardCharsets.UTF_8));
    }

    protected void handleInvalidConnectHeader(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
        if (this.logger.isErrorEnabled()) {
            this.logger.error("Handshake failed due to invalid Connection header " + request.getHeaders().getConnection());
        }

        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getBody().write("\"Connection\" must be \"upgrade\".".getBytes(StandardCharsets.UTF_8));
    }

    protected boolean isWebSocketVersionSupported(WebSocketHttpHeaders httpHeaders) {
        String version = httpHeaders.getSecWebSocketVersion();
        String[] supportedVersions = this.getSupportedVersions();
        String[] var4 = supportedVersions;
        int var5 = supportedVersions.length;

        for(int var6 = 0; var6 < var5; ++var6) {
            String supportedVersion = var4[var6];
            if (supportedVersion.trim().equals(version)) {
                return true;
            }
        }

        return false;
    }

    protected String[] getSupportedVersions() {
        return this.requestUpgradeStrategy.getSupportedVersions();
    }

    protected void handleWebSocketVersionNotSupported(ServerHttpRequest request, ServerHttpResponse response) {
        if (this.logger.isErrorEnabled()) {
            String version = request.getHeaders().getFirst("Sec-WebSocket-Version");
            this.logger.error("Handshake failed due to unsupported WebSocket version: " + version + ". Supported versions: " + Arrays.toString(this.getSupportedVersions()));
        }

        response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
        response.getHeaders().set("Sec-WebSocket-Version", StringUtils.arrayToCommaDelimitedString(this.getSupportedVersions()));
    }

    protected boolean isValidOrigin(ServerHttpRequest request) {
        return true;
    }

    @Nullable
    protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
        List<String> handlerProtocols = this.determineHandlerSupportedProtocols(webSocketHandler);
        Iterator<String> var4 = requestedProtocols.iterator();

        String protocol;
        do {
            if (!var4.hasNext()) {
                return null;
            }

            protocol = var4.next();
            if (handlerProtocols.contains(protocol.toLowerCase())) {
                return protocol;
            }
        } while(!this.supportedProtocols.contains(protocol.toLowerCase()));

        return protocol;
    }

    protected final List<String> determineHandlerSupportedProtocols(WebSocketHandler handler) {
        WebSocketHandler handlerToCheck = WebSocketHandlerDecorator.unwrap(handler);
        List<String> subProtocols = null;
        if (handlerToCheck instanceof SubProtocolCapable) {
            subProtocols = ((SubProtocolCapable)handlerToCheck).getSubProtocols();
        }

        return subProtocols != null ? subProtocols : Collections.emptyList();
    }

    protected List<WebSocketExtension> filterRequestedExtensions(ServerHttpRequest request, List<WebSocketExtension> requestedExtensions, List<WebSocketExtension> supportedExtensions) {
        List<WebSocketExtension> result = new ArrayList<>(requestedExtensions.size());
        Iterator<WebSocketExtension> var5 = requestedExtensions.iterator();

        while(var5.hasNext()) {
            WebSocketExtension extension = var5.next();
            if (supportedExtensions.contains(extension)) {
                result.add(extension);
            }
        }

        return result;
    }

    @Nullable
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        Method method = authenticatorObj.getClass().getMethod(invokeMethodName, ServerHttpRequest.class);

        return Optional.ofNullable((Principal) method.invoke(authenticatorObj,request))
                .orElse(request.getPrincipal());

    }

    static {
        ClassLoader classLoader = AbstractHandshakeHandler.class.getClassLoader();
        jettyWsPresent = ClassUtils.isPresent("org.eclipse.jetty.websocket.server.WebSocketServerFactory", classLoader);
        tomcatWsPresent = ClassUtils.isPresent("org.apache.tomcat.websocket.server.WsHttpUpgradeHandler", classLoader);
        undertowWsPresent = ClassUtils.isPresent("io.undertow.websockets.jsr.ServerWebSocketContainer", classLoader);
        glassfishWsPresent = ClassUtils.isPresent("org.glassfish.tyrus.servlet.TyrusHttpUpgradeHandler", classLoader);
        weblogicWsPresent = ClassUtils.isPresent("weblogic.websocket.tyrus.TyrusServletWriter", classLoader);
        websphereWsPresent = ClassUtils.isPresent("com.ibm.websphere.wsoc.WsWsocServerContainer", classLoader);
    }
}
