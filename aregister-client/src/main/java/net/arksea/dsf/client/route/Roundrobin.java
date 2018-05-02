package net.arksea.dsf.client.route;

import net.arksea.dsf.client.Instance;
import java.util.List;
import java.util.Optional;

/**
 * 轮询的路由策略，所有在线服务实例拥有相同的权重
 * @author arksea
 */
public class Roundrobin implements IRouteStrategy {

    private int index = 0;

    @Override
    public Optional<Instance> getInstance(List<Instance> list) {
        for (int i = 0; i < list.size(); ++i) {
            if (index >= list.size()) {
                index = 0;
            }
            Instance s = list.get(index);
            //System.out.println("size="+list.size()+"======"+index+"========"+status);
            ++index;
            if (s.check()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
