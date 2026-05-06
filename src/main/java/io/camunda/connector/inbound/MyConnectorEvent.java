package io.camunda.connector.inbound;

import io.camunda.connector.inbound.subscription.NextcloudFileEvent;

/**
 * The typed event wrapper that represents the data model passed into the Camunda process.
 *
 * This record is what gets serialized and stored as process variables.
 */
public record MyConnectorEvent(NextcloudFileEvent event) {}