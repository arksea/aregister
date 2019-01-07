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
        return 5000;
    }

    //根据quality决定是否进行限流， 返回值 <= 0则不做限流，其他值为每秒访问次数
    default int rateLimit(InstanceQuality quality) {
        return 0;
    }

    //根据quality判断状态切换，每个周期会调用一次
    boolean offlineToUp(InstanceQuality quality);
    boolean upToOnline(InstanceQuality quality);
    boolean upToOffline(InstanceQuality quality);
    boolean onlineToOffline(InstanceQuality quality);
}

