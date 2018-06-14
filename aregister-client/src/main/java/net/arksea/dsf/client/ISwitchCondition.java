package net.arksea.dsf.client;

/**
 * 服务切换上下线条件配置
 * Created by xiaohaixing on 2018/5/8.
 */
public interface ISwitchCondition {
    //一个统计周期长度(秒)，建议设置成10s 至 60s，默认为20s，网络环境越好，配置的周期可以越短
    default long statPeriod() {
        return 20;
    }
    //请求响应超过此时间（毫秒）将判为超时错误
    default long requestTimeout() {
        return 10000;
    }

    //默认实现：3个周期内正确率高于80%，将服务从offline状态切换为up状态
    default boolean offlineToUp(InstanceQuality quality) {
        int step = 3;
        float succeedRate = quality.getSucceedRate(step);
        long requestCount = quality.getRequestCount(step);
        return requestCount > 0 && succeedRate > 0.8f;
    }

    //默认实现：15个周期内正确率高于90%，将服务从up状态切换为online状态
    //注意：UP状态时Router将调度部分流量到此实例做灰度测试，实际比例看路由策略实现
    default boolean upToOnline(InstanceQuality quality) {
        int step = 15;
        float succeedRate = quality.getSucceedRate(step);
        long requestCount = quality.getRequestCount(step);
        return requestCount>0 && succeedRate > 0.9f;
    }

    //默认实现：当3个周期内正确率低于70%，将服务切换为offline状态
    default boolean upToOffline(InstanceQuality quality) {
        int step = 3;
        if (quality.getRequestCount(step) < 10) { //请求量太少不做切换
            return false;
        } else {
            float succeedRate = quality.getSucceedRate(step);
            return succeedRate < 0.7f;
        }
    }
    //默认实现：当3个周期内正确率低于70%，将服务切换为offline状态
    default boolean onlineToOffline(InstanceQuality quality) {
        int step = 3;
        if (quality.getRequestCount(step) < 10) { //请求量太少不做切换
            return false;
        } else {
            float succeedRate = quality.getSucceedRate(step);
            return succeedRate < 0.7f;
        }
    }
}

