package io.camunda.connector.inbound;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public record MyConnectorProperties(
        @NotNull
        @TemplateProperty(
                id = "webhookPort",
                label = "Webhook Port",
                group = "properties",
                description = "Port the connector listens on for Nextcloud events")
        int webhookPort,

        @NotNull
        @TemplateProperty(
                id = "webhookPath",
                label = "Webhook Path",
                group = "properties",
                description = "HTTP path to receive events, e.g. /nextcloud-webhook")
        String webhookPath
) {}
