package org.speleodb.ariane.plugin.speleodb;

/**
 * Exception indicating the server returned HTTP 304 Not Modified for an upload.
 * Used to signal that no changes were detected and the project was not saved.
 */
public class NotModifiedException extends Exception {
    public NotModifiedException(String message) {
        super(message);
    }
}
