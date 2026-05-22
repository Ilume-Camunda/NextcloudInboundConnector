package io.camunda.connector.inbound;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.inbound.subscription.NextcloudFileEvent;
import io.camunda.connector.inbound.subscription.NextcloudWebhookServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Nextcloud File Connector", type = "io.camunda:my-inbound-connector:1")
@ElementTemplate(
        id = "io.camunda.connector.Template.v1",
        name = "Nextcloud File Connector",
        version = 1,
        description = "Receives file events from Nextcloud via webhook",
        icon = "ilume_logo.svg",
        documentationRef = "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/",
        propertyGroups = {
                @ElementTemplate.PropertyGroup(id = "properties", label = "Properties"),
        },
        inputDataClass = MyConnectorProperties.class)
public class MyConnectorExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

  private static final Logger LOG = LoggerFactory.getLogger(MyConnectorExecutable.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DIRECTORY_MIME_TYPE = "httpd/unix-directory";

  private NextcloudWebhookServer webhookServer;
  private InboundConnectorContext context;
  private List<String> pathWhitelist;

  /**
   * When a BPMN diagram containing an inbound connector element
   * (start event, intermediate catch event, boundary event, or receive task) is deployed
   * the connector runtime detects the newly deployed process definition and identifies
   * any inbound connector elements within it.
   *
   * It uses two data sources to track:
   *
   * 1.) Latest versions of process definitions
   * 2.) Older versions that still have active message subscriptions
   *
   * Before creating a new executable, the runtime checks whether an existing active
   * executable can be reused (deduplicated) for the new connector element.
   * If reuse is possible, it updates the existing executable rather than creating a new one.
   * If no existing executable can be reused, the runtime creates a new executable — an
   * instance of the inbound connector managed by the runtime.
   *
   * The runtime then calls the activate() method. This is where the connector:
   *
   *     - Reads its configuration via connectorContext.bindProperties(...)
   *     - Sets up its subscription, listener, or polling mechanism
   *     - Starts receiving external events
   */
  @Override
  public void activate(InboundConnectorContext connectorContext) throws Exception {
    this.context = connectorContext;
    this.pathWhitelist = parseWhitelist(readWhitelistFromProperties());
    var props = connectorContext.bindProperties(MyConnectorProperties.class);

    // Start the webhook HTTP server on the configured port and path.
    webhookServer = new NextcloudWebhookServer(
            props.webhookPort(),
            props.webhookPath(),
            this::onEvent
    );
    LOG.info("Nextcloud connector activated on port {}{} | whitelist: {}",
            props.webhookPort(), props.webhookPath(),
            pathWhitelist.isEmpty() ? "ALL PATHS ALLOWED" : pathWhitelist);
  }

  private static String readWhitelistFromProperties() {
    try (var input = MyConnectorExecutable.class
            .getClassLoader()
            .getResourceAsStream("application.properties")) {
      if (input == null) return "";
      var props = new java.util.Properties();
      props.load(input);
      return props.getProperty("nextcloud.path.whitelist", "");
    } catch (Exception e) {
      return "";
    }
  }

  // Called by NextcloudWebhookServer each time a valid webhook POST is received.
  private void onEvent(NextcloudFileEvent rawEvent) {

    if (!isEventAllowed(rawEvent)) {
      return; // reason already logged inside isEventAllowed
    }

    // Convert the typed NextcloudFileEvent record into a flat Map<String, Object>.
    // This is necessary because Camunda's FEEL engine evaluates expressions like
    // "node.internalPath" or "eventType" against a generic variable map, not typed Java objects.
    var variables = MAPPER.convertValue(rawEvent, new TypeReference<Map<String, Object>>() {});
    LOG.info("Variables map: {}", variables);

    // Wrap the variables in a CorrelationRequest and pass it to Camunda.
    // Camunda will then:
    //   - evaluate correlationKeyExpression (from BPMN) against these variables to find a key
    //   - evaluate messageIdExpression to check for duplicate events
    //   - find the waiting process instance whose subscription key matches
    //   - inject the variables into that instance and resume it
    var correlationRequest = CorrelationRequest.builder()
            .variables(variables)
            .build();
    var result = context.correlate(correlationRequest);
    handleResult(result);
  }

  /**
   * Returns true only if the event passes both guards:
   *
   *   1. Not a directory — events with mimeType "httpd/unix-directory" are folder
   *      operations (create/rename/delete) and should never trigger the process.
   *
   *   2. Path whitelist — if NEXTCLOUD_PATH_WHITELIST is set, the event's node.path
   *      must contain at least one of the listed substrings. If the env var is absent
   *      or blank, all paths are allowed.
   */
  boolean isEventAllowed(NextcloudFileEvent event) {

    if (event.node() != null && DIRECTORY_MIME_TYPE.equals(event.node().mimeType())) {
      LOG.debug("Event skipped - node is a directory (mimeType={}), path: {}",
              DIRECTORY_MIME_TYPE, event.node().path());
      return false;
    }

    if (pathWhitelist.isEmpty()) {
      return true; // no whitelist configured → allow all
    }
    if (event.node() == null || event.node().path() == null) {
      LOG.warn("Event blocked - no node path to check against whitelist");
      return false;
    }
    String nodePath = event.node().path();
    boolean allowed = pathWhitelist.stream().anyMatch(nodePath::contains);
    if (allowed) {
      LOG.debug("Event allowed - path '{}' matched whitelist {}", nodePath, pathWhitelist);
    } else {
      LOG.debug("Event blocked - path '{}' not in whitelist {}", nodePath, pathWhitelist);
    }
    return allowed;
  }

  /**
   * Parses the comma-separated env var value into a trimmed, non-empty list.
   * Returns an empty list if the input is null or blank (= allow all).
   *
   * Example env var value: "/invoices,/contracts,/hr"
   */
  static List<String> parseWhitelist(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
  }

  void setPathWhitelist(List<String> whitelist) { this.pathWhitelist = whitelist; }

  // Handles the result returned by Camunda after attempting correlation.
  private void handleResult(CorrelationResult result) {
    if (result == null) return;
    switch (result) {
      case CorrelationResult.Success ignored ->
              LOG.info("Event correlated successfully");
      case CorrelationResult.Failure failure -> {
        switch (failure.handlingStrategy()) {
          case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream ignored -> {
            if (failure.message() != null &&
                    failure.message().contains("ALREADY_EXISTS")) {
              LOG.debug("Duplicate event suppressed (node.id {} already processed)", "241");
            } else {
              LOG.error("Correlation failed: {}", failure.message());
            }
          }
          case CorrelationFailureHandlingStrategy.Ignore ignored ->
                  LOG.debug("Correlation ignored: {}", failure.message());
        }
      }
    }
  }

  @Override
  public void deactivate() {
    if (webhookServer != null) webhookServer.stop();
    LOG.info("Nextcloud connector deactivated");
  }
}