package io.camunda.connector.inbound.subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NextcloudFileEvent(
        String eventType,
        String eventName,
        NodeInfo node,
        WorkflowFile workflowFile
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeInfo(
            String internalPath,
            String mimeType,
            long id,
            long modifiedTime,
            String path,
            long size,
            @JsonProperty("Etag") String etag,
            boolean isDeletable,
            boolean isShareable,
            boolean isUpdatable,
            int permissions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowFile(
            String displayText,
            String url
    ) {}
}
