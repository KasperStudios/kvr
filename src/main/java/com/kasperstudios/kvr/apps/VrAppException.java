package com.kasperstudios.kvr.apps;

/**
 * Why a {@code .vrapp} was rejected. The code name travels over the network and reaches the App Market
 * (and JS) as a stable string; the message is for logs and the status line.
 */
public final class VrAppException extends Exception {

    public enum Code {
        BAD_ZIP, BAD_MANIFEST, BAD_PATH, BAD_FILE_TYPE, TOO_LARGE, TOO_MANY_FILES, MISSING_MAIN, BAD_MODULE,
        RESERVED_ID, NOT_ALLOWED, QUOTA, NO_GLASSES, HASH_MISMATCH, NOT_FOUND, BUSY, RATE_LIMIT, IO
    }

    private final Code code;

    public VrAppException(Code code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
