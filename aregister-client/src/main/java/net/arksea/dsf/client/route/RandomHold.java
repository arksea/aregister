package net.arksea.dsf.client.route;

import net.arksea.dsf.client.Instance;
import net.arksea.dsf.client.InstanceStatus;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 首次请求时在可用实例中随机指定一个，并一直使用此实例，直到其Offline时再随机指定一个可用实例
 * Created by xiaohaixing on 2018/4/20.
 */
public class RandomHold implements IRouteStrategy {

    private final Random random;
    private Instance current;
    public RandomHold() {
        random = new Random();
    }

    @Override
    public Optional<Instance> getInstance(List<Instance> list) {
        if (current != null && current.getStatus() == InstanceStatus.ONLINE) {
            return Optional.of(current);
        } else {
            List<Instance> onlines = new LinkedList<>();
            list.forEach(it -> {
                if (it.getStatus() == InstanceStatus.ONLINE) {
                    onlines.add(it);
                }
            });
            if (!onlines.isEmpty()) {
                int index = random.nextInt(onlines.size());
                current = onlines.get(index);
                return Optional.of(current);
            } else if (current == null) {
                return Optional.empty();
            } else {
                return Optional.of(current);
            }
        }
    }
}
