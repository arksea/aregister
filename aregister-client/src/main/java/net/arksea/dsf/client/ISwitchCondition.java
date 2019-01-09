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
    //请求响应超过此时间(毫秒)将判为超时错误
    default long requestTimeout() {
        return 5000;
    }

    //平均请求响应超过Max将会被切换到UP限流状态，当低于Min将切换到Online取消限流
    //Min与Max不能靠的太近，否则容易引起频繁的状态切换
    default long rateLimitRequestTimeMax() {
        return 2000;
    }
    default long rateLimitRequestTimeMin() {
        return 1000;
    }

    //UP状态时限制使用1/rateLimitMod的流量进行访问
    default int rateLimitMod() { return 10; }

    //根据Ping成功率决定是否切换到UP状态做1/rateLimitMod灰度测试
    boolean offlineToUp(InstanceQuality quality);
    //根据灰度测试成功率决定是否切换为在线状态
    boolean upToOnline(InstanceQuality quality);
    //根据请求成功率做熔断
    boolean upToOffline(InstanceQuality quality);
    boolean onlineToOffline(InstanceQuality quality);
    //根据请求响应时间做限流， 服务在UP状态时将被限流，Router只会调度部分流量进行访问
    boolean onlineToUp(InstanceQuality quality);
}
