package io.camunda.connector.inbound.integration;

import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.inbound.MyConnectorExecutable;
import io.camunda.connector.inbound.MyConnectorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MyConnectorIntegrationTest {

  private static final String WEBHOOK_PATH = "/somebody";

  @Mock
  private InboundConnectorContext context;

  private MyConnectorExecutable connector;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    port = findFreePort();
    connector = new MyConnectorExecutable();
    when(context.bindProperties(MyConnectorProperties.class))
            .thenReturn(new MyConnectorProperties(port, WEBHOOK_PATH));
    connector.activate(context);
  }

  @AfterEach
  void tearDown() {
    connector.deactivate();
  }

  @Test
  void shouldParseWebhookAndPassCorrectVariablesToCorrelate() throws Exception {
    // when
    int status = sendPost("""
                {
                  "eventType": "NODE_CREATED",
                  "eventName": "\\\\OCP\\\\Files\\\\Events\\\\Node\\\\NodeCreatedEvent",
                  "node": {
                    "internalPath": "files/documents/report.pdf",
                    "mimeType": "application/pdf",
                    "id": 42,
                    "modifiedTime": 1700000000,
                    "path": "/admin/files/documents/report.pdf",
                    "size": 204800,
                    "Etag": "abc123",
                    "isDeletable": true,
                    "isShareable": true,
                    "isUpdatable": true,
                    "permissions": 31
                  },
                  "workflowFile": {
                    "displayText": "report.pdf",
                    "url": "https://nextcloud.example.com/f/42"
                  }
                }
                """);

    // then — server accepted the request
    assertThat(status).isEqualTo(200);

    // and correlate() was called once with the correctly mapped variables
    ArgumentCaptor<CorrelationRequest> captor =
            ArgumentCaptor.forClass(CorrelationRequest.class);
    verify(context, times(1)).correlate(captor.capture());

    @SuppressWarnings("unchecked")
    var vars = (Map<String, Object>) captor.getValue().getVariables();

    assertThat(vars.get("eventType")).isEqualTo("NODE_CREATED");

    @SuppressWarnings("unchecked")
    var node = (Map<String, Object>) vars.get("node");
    assertThat(node.get("internalPath")).isEqualTo("files/documents/report.pdf");
    assertThat(node.get("mimeType")).isEqualTo("application/pdf");
    assertThat(((Number) node.get("id")).longValue()).isEqualTo(42L);

    @SuppressWarnings("unchecked")
    var workflowFile = (Map<String, Object>) vars.get("workflowFile");
    assertThat(workflowFile.get("url")).isEqualTo("https://nextcloud.example.com/f/42");
  }

  @Test
  void shouldReturn405ForNonPostRequest() throws Exception {
    var response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + WEBHOOK_PATH))
                    .GET()
                    .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(response.statusCode()).isEqualTo(405);
    verify(context, never()).correlate(any());
  }

  @Test
  void shouldReturn400ForMalformedJson() throws Exception {
    int status = sendPost("{ not valid json }");

    assertThat(status).isEqualTo(400);
    verify(context, never()).correlate(any());
  }

  private int sendPost(String json) throws Exception {
    var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + WEBHOOK_PATH))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    return HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.discarding())
            .statusCode();
  }

  // Asks the OS for a free port by binding to port 0, then immediately releasing it.
  private static int findFreePort() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}