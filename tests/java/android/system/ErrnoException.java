package android.system;

/** Test-only checked exception, preserving the underlying filesystem failure. */
public final class ErrnoException extends Exception {
    public ErrnoException(String function, Throwable cause) {
        super(function + ": " + cause.getMessage(), cause);
    }
}
