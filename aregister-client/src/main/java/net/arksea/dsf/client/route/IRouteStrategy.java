package net.arksea.dsf.client.route;

import net.arksea.dsf.client.Instance;

import java.util.List;
import java.util.Optional;

/**
 * 路由策略接口
 * @author arksea
 */
public interface IRouteStrategy {
    /*
     * 获取服务实例
     * @param  service 所请求服务所有实例的引用
     * @return  返回服务实例地址
     * @throw   NoUseableServiceException 没有可用的在线服务
     * @throw   RateLimitedException 所有服务都达到流控门限
     */
    Optional<Instance> getInstance(List<Instance> list);
}
