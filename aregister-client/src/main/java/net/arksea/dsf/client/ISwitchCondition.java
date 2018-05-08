package net.arksea.dsf.client;

/**
 * 服务切换上下线条件配置
 * Created by xiaohaixing on 2018/5/8.
 */
public interface ISwitchCondition {
    //一个统计周期长度(秒)，建议设置成10s 至 60s，默认为10s，网络环境越好，配置的周期可以越短
    default long statPeriod() {
        return 10;
    }
    //请求超过此时间（秒）判为超时
    default long requestTimeout() {
        return 10;
    }
    //默认实现：3个周期内超时率低于10%，将服务从offline状态切换为up状态
    //此时Router将调度正常状态时1/10的流量到此实例，直到被upToOnline将其切换为其他状态
    default boolean offlineToUp(InstanceQuality quality) {
        float timeoutRate = quality.getTimeoutRate(3);
        long requestCount = quality.getRequestCount(3);
        return requestCount > 0 && timeoutRate < 0.1f;
    }
    //默认实现：5个周期内超时率低于10%，将服务从up状态切换为online状态
    default boolean upToOnline(InstanceQuality quality) {
        float timeoutRate = quality.getTimeoutRate(5);
        long requestCount = quality.getRequestCount(5);
        return requestCount>0 && timeoutRate < 0.1f;
    }
    //默认实现：当3个周期内服务超时率高于30%，将服务切换为offline状态
    default boolean upToOffline(InstanceQuality quality) {
        float timeoutRate = quality.getTimeoutRate(3);
        return timeoutRate > 0.3f;
    }
    //默认实现：当2个周期内服务超时率高于40%，将服务切换为offline状态
    default boolean onlineToOffline(InstanceQuality quality) {
        float timeoutRate = quality.getTimeoutRate(2);
        return timeoutRate > 0.4f;
    }
}

