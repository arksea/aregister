package net.arksea.dsf.service;

/**
 * 默认流控策略
 * 1、平均响应时间大于上门限，则降低限流流量，直到最小限流流量
 * 2、平均响应时间小于下门限，则提高限流流量，直到不再限流
 * Created by xiaohaixing on 2019/1/8.
 */
public class DefaultRateLimitStrategy implements IRateLimitStrategy {
    private IRateLimitConfig rateLimitConfig;

    public DefaultRateLimitStrategy(IRateLimitConfig rateLimitConfig) {
        if (rateLimitConfig.getLowThreshold() >= rateLimitConfig.getHightThreshold()) {
            throw new IllegalArgumentException("lowThreshold greater than hightThreshold");
        }
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public long getLimitQPS(long meanRequestTime, long meanQPS, long lastLimitQPS) {
        final long  LOW_THRESHOLD   = rateLimitConfig.getLowThreshold();
        final long  HIGHT_THRESHOLD = rateLimitConfig.getHightThreshold();
        final float UPDATE_RATIO    = rateLimitConfig.getUpdateRatio();
        final int   MIN_LIMIT_QPS   = rateLimitConfig.getMinLimitQps();
        if (meanRequestTime < LOW_THRESHOLD) {
            if (lastLimitQPS - meanQPS > meanQPS) {
                //当限流QPS已经远大于实际QPS，则不再需要做限流了
                return 0;
            } else {
                return lastLimitQPS == 0 ? 0 : (int) (lastLimitQPS * UPDATE_RATIO);
            }
        } else if (meanRequestTime > HIGHT_THRESHOLD) {
            if (lastLimitQPS == 0) {
                return Math.max(MIN_LIMIT_QPS, (int)(meanQPS / UPDATE_RATIO));
            } else {
                return Math.max(MIN_LIMIT_QPS, (int)(lastLimitQPS / UPDATE_RATIO));
            }
        } else {
            return Math.max(MIN_LIMIT_QPS, lastLimitQPS);
        }
    }

    public Object getRateLimitResponse() {
        return rateLimitConfig.getRateLimitResponse();
    }
}
