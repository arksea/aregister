package net.arksea.dsf.service;

/**
 *
 * Created by xiaohaixing on 2019/1/8.
 */
public interface IRateLimitStrategy {

    /**
     * 根据平均请求响应时间确定限流QPS, <=0表示不做限流
     * @param meanRequestTime 实际平均响应时间
     * @param meanQPS         实际平均QPS
     * @param lastLimitQPS    目前限流QPS
     * @return
     */
    default long getLimitQPS(long meanRequestTime, long meanQPS, long lastLimitQPS) {
        return 0;
    }

    /**
     * 限流QPS最小修改间隔，单位为分钟
     * @return
     */
    default long getUpdatePeriodMinutes() {
        return 2;
    }

    /**
     * 当请求被限流时的返回值
     * @return
     */
    Object getRateLimitResponse();
}
