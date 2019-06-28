package net.arksea.dsf.client;

/**
 *
 * Created by xiaohaixing on 2018/5/8.
 */
public class DefaultSwitchCondition implements ISwitchCondition {

    //根据Ping成功率决定是否切换到UP状态做1/rateLimitMod灰度测试
    //默认实现：10个周期内正确率高于80%，将服务从offline状态切换为up状态
    @Override
    public boolean offlineToUp(InstanceQuality quality) {
        int step = 10;
        float succeedRate = quality.getSucceedRate(step);
        long requestCount = quality.getRequestCount(step);
        return requestCount > 0 && succeedRate > 0.8f;
    }

    //根据灰度测试成功率决定是否切换为在线状态
    //默认实现：10个周期内正确率高于90%，将服务从up状态切换为online状态
    //注意：UP状态时Router将调度部分流量到此实例做灰度测试，实际比例看路由策略实现
    @Override
    public boolean upToOnline(InstanceQuality quality) {
        int step = 10;
        float succeedRate = quality.getSucceedRate(step);
        long requestCount = quality.getRequestCount(step);
        return requestCount > 0 && succeedRate > 0.9f && getMeanRespondTime(quality) < rateLimitRequestTimeMin();
    }

    //根据请求成功率做熔断
    //默认实现：当3个周期内正确率低于50%，将服务切换为offline状态
    @Override
    public boolean upToOffline(InstanceQuality quality) {
        return onlineToOffline(quality);
    }
    @Override
    public boolean onlineToOffline(InstanceQuality quality) {
        int step = 3;
        if (quality.getRequestCount(step) < 10) { //请求量太少不做切换
            return false;
        } else {
            float succeedRate = quality.getSucceedRate(step);
            return succeedRate < 0.5f;
        }
    }

    //根据请求响应时间做限流， 服务在UP状态时将被限流，Router只会调度部分流量进行访问
    @Override
    public boolean onlineToUp(InstanceQuality quality) {
        return getMeanRespondTime(quality) > rateLimitRequestTimeMax();
    }

    private long getMeanRespondTime(InstanceQuality quality) {
        return quality.getMeanRespondTime(15);
    }
}
