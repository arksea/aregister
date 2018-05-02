package net.arksea.dsf;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public interface ServiceResponse {
    String getRequestId();

    /**
     * 业务请求是否成功，请求的成功率可以被用于诊断服务是否可用，
     * 当成功率小于用户指定的门限时，将不会对其发起请求，
     * 不需要这种特性的可以在实现中返回常量true，或者设置一个小于0的门限
     * @return
     */
    boolean isSucceed();
}
