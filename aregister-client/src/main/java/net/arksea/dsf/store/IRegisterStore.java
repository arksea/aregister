package net.arksea.dsf.store;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/4/19.
 */
public interface IRegisterStore {
    List<Instance> getServiceInstances(String name);
    void addServiceInstance(String name, Instance instance);
    void delServiceInstance(String name, String addr);
    boolean serviceExists(String name);
    void delService(String name);
    String getVersionID(String name); //每次修改都必须更新VersionID，注册服务根据此ID判断是否需要更新实例列表
}
