package net.arksea.dsf.store;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/4/19.
 */
public interface IRegisterStore {
    List<Instance> getServiceInstances(String name);
    boolean addServiceInstance(Instance instance);
    boolean delServiceInstance(String name, String addr);
}
