package net.arksea.dsf.store;

/**
 *
 * Created by xiaohaixing on 2018/4/19.
 */
public class RegisterStoreException extends RuntimeException {
    public RegisterStoreException() {
        super();
    }

    public RegisterStoreException(String message) {
        super(message);
    }

    public RegisterStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public RegisterStoreException(Throwable cause) {
        super(cause);
    }
}
