package net.arksea.dsf.client;

/**
 *
 * @author xiaohaixing
 */
public class NoUseableServiceException extends RuntimeException {
    public NoUseableServiceException(String name) {
        super("Service "+name+" has not useable instances");
    }
}
