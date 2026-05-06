package io.camunda.connector.inbound.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

/**
 * A lightweight embedded HTTP server that listens for incoming webhook POSTs from Nextcloud.
 *
 * Lifecycle:
 *   - Created and started in MyConnectorExecutable.activate()
 *   - Stopped in MyConnectorExecutable.deactivate()
 *
 * When a valid POST arrives, the JSON body is deserialized into a NextcloudFileEvent
 * and passed to the callback provided at construction time (MyConnectorExecutable::onEvent).
 */
public class NextcloudWebhookServer {

    private static final Logger LOG = LoggerFactory.getLogger(NextcloudWebhookServer.class);
    // Mapper used to deserialize the raw JSON body into a typed NextcloudFileEvent
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;

    public NextcloudWebhookServer(int port, String webhookPath, Consumer<NextcloudFileEvent> callback) throws IOException {
        // Bind the server to the given port on all network interfaces (0.0.0.0)
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register a handler for the specific webhook path.
        // Only POST requests are accepted - all other HTTP methods return 405 Method Not Allowed.
        server.createContext(webhookPath, exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                // Read the full request body as raw bytes and deserialize it into
                // a NextcloudFileEvent.
                var body = exchange.getRequestBody().readAllBytes();
                var event = MAPPER.readValue(body, NextcloudFileEvent.class);
                LOG.info("Received Nextcloud event for path: {}", event.node().path());
                callback.accept(event);
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception e) {
                LOG.error("Failed to parse Nextcloud event", e);
                exchange.sendResponseHeaders(400, -1);
            } finally {
                exchange.close();
            }
        });

        server.start();
        LOG.info("Webhook server started on port {} at path {}", port, webhookPath);

        /*var bodyBytes = exchange.getRequestBody().readAllBytes();
        LOG.info("Raw body: {}", new String(bodyBytes, StandardCharsets.UTF_8));
        var event = MAPPER.readValue(bodyBytes, NextcloudFileEvent.class);*/
    }

    public void stop() {
        LOG.info("Stopping webhook server");
        server.stop(1);
    }
}
