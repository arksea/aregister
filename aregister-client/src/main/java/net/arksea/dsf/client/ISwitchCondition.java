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
        return 3000;
    }

    boolean offlineToUp(InstanceQuality quality);
    boolean upToOnline(InstanceQuality quality);
    boolean upToOffline(InstanceQuality quality);
    boolean onlineToOffline(InstanceQuality quality);
}

