package net.arksea.dsf.client.route;

import net.arksea.dsf.client.Instance;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 热备的路由策略，按服务器地址排序，选择第一个在线的服务
 * @author arksea
 */
public class HotStandby implements IRouteStrategy {

    @Override
    public Optional<Instance> getInstance(List<Instance> list, int checkMod) {
        TreeSet<Instance> sorted = new TreeSet<>();
        sorted.addAll(list);
        for (Instance s : sorted) {
            if (s.check(checkMod)) {
                return Optional.of(s);
            }
        }
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(sorted.first());
        }
    }
}
