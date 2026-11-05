package com.kasperstudios.kvr.vfs;

/** Why a VFS operation failed; the code string reaches JS as {@code err.code}. */
public final class VfsException extends Exception {

    public enum Code { INVALID_PATH, DENIED, NOT_FOUND, IS_DIRECTORY, NOT_EMPTY, QUOTA_BYTES, QUOTA_FILES, TOO_LARGE, NO_GLASSES, RATE_LIMIT, IO }

    private final Code code;

    public VfsException(Code code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
