package net.arksea.dsf.service;

/**
 * 默认流控策略
 * 1、平均响应时间大于上门限，则降低限流流量，直到最小限流流量
 * 2、平均响应时间小于下门限，则提高限流流量，直到不再限流
 * Created by xiaohaixing on 2019/1/8.
 */
public class DefaultRateLimitStrategy implements IRateLimitStrategy {
    private final Object rateLimitResponse;
    private final long lowThreshold;  //响应时间下门限
    private final long hightThreshold; //响应时间上门限
    private final static int MIN_QPS = 10;  //最小限流流量
    private final static float RATIO = 1.2f; //调整限流流量的系数

    /**
     *
     * @param rateLimitResponse 当请求被限流时的返回值
     * @param lowThreshold 响应时间下门限
     * @param hightThreshold 响应时间上门限，必须大于下门限
     */
    public DefaultRateLimitStrategy(Object rateLimitResponse, long lowThreshold, long hightThreshold) {
        this.rateLimitResponse = rateLimitResponse;
        this.lowThreshold = lowThreshold;
        this.hightThreshold = hightThreshold;
        if (lowThreshold >= hightThreshold) {
            throw new IllegalArgumentException("lowThreshold greater than hightThreshold");
        }
    }

    @Override
    public long getLimitQPS(long meanRequestTime, long meanQPS, long lastLimitQPS) {
        if (meanRequestTime < lowThreshold) {
            if (lastLimitQPS - meanQPS > meanQPS) {
                //当限流QPS已经远大于实际QPS，则不再需要做限流了
                return 0;
            } else {
                return lastLimitQPS == 0 ? 0 : (int) (lastLimitQPS * RATIO);
            }
        } else if (meanRequestTime > hightThreshold) {
            if (lastLimitQPS == 0) {
                return meanQPS > MIN_QPS ? (int)(meanQPS / RATIO) : MIN_QPS;
            } else {
                return lastLimitQPS > MIN_QPS ? (int)(lastLimitQPS / RATIO) : MIN_QPS;
            }
        } else {
            return lastLimitQPS;
        }
    }

    public Object getRateLimitResponse() {
        return rateLimitResponse;
    }
}
