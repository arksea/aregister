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

    @SuppressWarnings("PMD.ForLoopCanBeForeach") //todo: 改写成foreach模式
    @Override
    public Optional<Instance> getInstance(List<Instance> list, int checkMod) {
        for (int i = 0; i < list.size(); ++i) {
            if (index >= list.size()) {
                index = 0;
            }
            Instance s = list.get(index);
            ++index;
            if (s.check(checkMod)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
