package net.arksea.dsf.client;

/**
 *
 * Created by xiaohaixing on 2018/5/8.
 */
public class DefaultSwitchCondition implements ISwitchCondition {

    //默认实现：3个周期内正确率高于80%，将服务从offline状态切换为up状态
    @Override
    public boolean offlineToUp(InstanceQuality quality) {
        int step = 3;
        float succeedRate = quality.getSucceedRate(step);
        long requestCount = quality.getRequestCount(step);
        return requestCount > 0 && succeedRate > 0.8f;
    }

    //默认实现：15个周期内正确率高于90%，将服务从up状态切换为online状态
    //注意：UP状态时Router将调度部分流量到此实例做灰度测试，实际比例看路由策略实现
    @Override
    public boolean upToOnline(InstanceQuality quality) {
        int step = 15;
        float succeedRate = quality.getSucceedRate(step);
        long requestCount = quality.getRequestCount(step);
        return requestCount>0 && succeedRate > 0.9f;
    }

    //默认实现：当3个周期内正确率低于70%，将服务切换为offline状态
    @Override
    public boolean upToOffline(InstanceQuality quality) {
        int step = 3;
        if (quality.getRequestCount(step) < 10) { //请求量太少不做切换
            return false;
        } else {
            float succeedRate = quality.getSucceedRate(step);
            return succeedRate < 0.7f;
        }
    }
    //默认实现：当3个周期内正确率低于70%，将服务切换为offline状态
    @Override
    public boolean onlineToOffline(InstanceQuality quality) {
        int step = 3;
        if (quality.getRequestCount(step) < 10) { //请求量太少不做切换
            return false;
        } else {
            float succeedRate = quality.getSucceedRate(step);
            return succeedRate < 0.7f;
        }
    }
}
