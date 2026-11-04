package com.aitchn.prism.platform.spi;

/** Thrown when the server is not Paper or a Paper fork. */
public final class UnsupportedPlatformException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedPlatformException(String message) {
        super(message);
    }
}
