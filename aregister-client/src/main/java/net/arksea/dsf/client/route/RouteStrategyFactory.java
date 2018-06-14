package net.arksea.dsf.client.route;

/**
 * 路由策略工厂类，根据类型串选择路由策略，默认为带权重的轮询策略
 * @author arksea
 */
public final class RouteStrategyFactory {

    private RouteStrategyFactory() {}
    public static IRouteStrategy create(RouteStrategy strategy) {
        switch (strategy) {
            case ROUNDROBIN:
                return new Roundrobin();
            case HOT_STANDBY:
                return new HotStandby();
            case RANDOM_ROUND:
                return new RandomRound();
            case RANDOM_HOLD:
                return new RandomHold();
            default:
                return new Roundrobin();
        }
    }
}
