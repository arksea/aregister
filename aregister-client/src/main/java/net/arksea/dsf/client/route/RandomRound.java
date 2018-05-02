package net.arksea.dsf.client.route;

import net.arksea.dsf.client.Instance;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RandomRound implements IRouteStrategy {

    private final Random random;
    public RandomRound() {
        random = new Random();
    }

    @Override
    public Optional<Instance> getInstance(List<Instance> list) {
        int index = list.size() > 1 ? random.nextInt(list.size()) : 0;
        for (int i = 0; i < list.size(); ++i) {
            Instance s = list.get(index);
            if (s.check()) {
                return Optional.of(s);
            }
            ++index;
            if (index > list.size()) {
                index = 0;
            }
        }
        return Optional.empty();
    }
}
