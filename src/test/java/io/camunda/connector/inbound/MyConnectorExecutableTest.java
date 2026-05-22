package io.camunda.connector.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.inbound.subscription.NextcloudFileEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class MyConnectorExecutableTest {

    private MyConnectorExecutable connector;

    @BeforeEach
    void setUp() {
        connector = new MyConnectorExecutable();
    }

    @Nested
    class ParseWhitelist {

        @Test
        void nullInputReturnsEmptyList() {
            assertThat(MyConnectorExecutable.parseWhitelist(null)).isEmpty();
        }

        @Test
        void blankInputReturnsEmptyList() {
            assertThat(MyConnectorExecutable.parseWhitelist("   ")).isEmpty();
        }

        @Test
        void singleEntryIsParsed() {
            assertThat(MyConnectorExecutable.parseWhitelist("/Photos"))
                    .containsExactly("/Photos");
        }

        @Test
        void multipleEntriesAreSplit() {
            assertThat(MyConnectorExecutable.parseWhitelist("/Photos,/invoices,/hr"))
                    .containsExactly("/Photos", "/invoices", "/hr");
        }

        @Test
        void entriesAreTrimmed() {
            assertThat(MyConnectorExecutable.parseWhitelist("  /Photos , /invoices  "))
                    .containsExactly("/Photos", "/invoices");
        }

        @Test
        void emptyEntriesBetweenCommasAreIgnored() {
            assertThat(MyConnectorExecutable.parseWhitelist("/Photos,,/invoices,"))
                    .containsExactly("/Photos", "/invoices");
        }
    }


    @Nested
    class DirectoryGuard {

        @Test
        void directoryIsAlwaysBlocked() {
            connector.setPathWhitelist(List.of()); // open whitelist
            var event = fileEvent("httpd/unix-directory", "/demo/files/Photos/some-folder");
            assertThat(connector.isEventAllowed(event)).isFalse();
        }

        @Test
        void directoryIsBlockedEvenWhenPathMatchesWhitelist() {
            connector.setPathWhitelist(List.of("/Photos"));
            var event = fileEvent("httpd/unix-directory", "/demo/files/Photos/some-folder");
            assertThat(connector.isEventAllowed(event)).isFalse();
        }

        @Test
        void nonDirectoryIsNotBlockedByDirectoryGuard() {
            connector.setPathWhitelist(List.of()); // open whitelist
            var event = fileEvent("image/jpeg", "/demo/files/Photos/img.jpg");
            assertThat(connector.isEventAllowed(event)).isTrue();
        }
    }

    @Nested
    class WhitelistGuard {

        @Test
        void emptyWhitelistAllowsEverything() {
            connector.setPathWhitelist(List.of());
            var event = fileEvent("image/jpeg", "/demo/files/Photos/img.jpg");
            assertThat(connector.isEventAllowed(event)).isTrue();
        }

        @Test
        void pathMatchingWhitelistEntryIsAllowed() {
            connector.setPathWhitelist(List.of("/Photos"));
            var event = fileEvent("image/jpeg", "/demo/files/Photos/img.jpg");
            assertThat(connector.isEventAllowed(event)).isTrue();
        }

        @Test
        void pathNotMatchingAnyEntryIsBlocked() {
            connector.setPathWhitelist(List.of("/invoices", "/contracts"));
            var event = fileEvent("image/jpeg", "/demo/files/Photos/img.jpg");
            assertThat(connector.isEventAllowed(event)).isFalse();
        }

        @Test
        void pathMatchingOneOfMultipleEntriesIsAllowed() {
            connector.setPathWhitelist(List.of("/invoices", "/Photos", "/hr"));
            var event = fileEvent("application/pdf", "/demo/files/Photos/report.pdf");
            assertThat(connector.isEventAllowed(event)).isTrue();
        }

        @Test
        void matchingIsSubstring() {
            connector.setPathWhitelist(List.of("/Photos"));
            var event = fileEvent("image/jpeg", "/demo/files/Photos/subfolder/img.jpg");
            assertThat(connector.isEventAllowed(event)).isTrue();
        }

        @Test
        void matchingIsCaseSensitive() {
            connector.setPathWhitelist(List.of("/photos")); // lowercase
            var event = fileEvent("image/jpeg", "/demo/files/Photos/img.jpg"); // uppercase P
            assertThat(connector.isEventAllowed(event)).isFalse();
        }

        @Test
        void nullNodeIsBlockedWhenWhitelistIsActive() {
            connector.setPathWhitelist(List.of("/Photos"));
            var event = new NextcloudFileEvent(null, null, null, null);
            assertThat(connector.isEventAllowed(event)).isFalse();
        }

        @Test
        void nullNodeIsAllowedWhenWhitelistIsEmpty() {
            connector.setPathWhitelist(List.of());
            var event = new NextcloudFileEvent(null, null, null, null);
            assertThat(connector.isEventAllowed(event)).isTrue();
        }
    }

    /**
     * Builds a minimal NextcloudFileEvent with only the fields isEventAllowed cares about:
     * node.mimeType and node.path. All other fields are set to safe defaults.
     */
    private static NextcloudFileEvent fileEvent(String mimeType, String path) {
        var node = new NextcloudFileEvent.NodeInfo(
                "files/some/internal/path",  // internalPath
                mimeType,                    // mimeType
                1L,                          // id
                0L,                          // modifiedTime
                path,                        // path
                0L,                          // size
                "etag",                      // etag
                true,                        // isDeletable
                true,                        // isShareable
                true,                        // isUpdatable
                31                           // permissions
        );
        return new NextcloudFileEvent("NODE_CREATED", "someEvent", node, null);
    }
}
