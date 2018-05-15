package net.arksea.dsf.client;

import net.arksea.dsf.DSF;
import net.arksea.dsf.store.LocalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 服务实例源，从本地缓存文件获取实例列表
 * Created by xiaohaixing on 2018/5/14.
 */
public class LocalInstanceSource implements IInstanceSource {
    private static final Logger log = LogManager.getLogger(LocalInstanceSource.class);

    protected final String serviceName;

    public LocalInstanceSource(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public DSF.SvcInstances getSvcInstances() throws Exception {
        return loadFromLocalCache();
    }

    protected DSF.SvcInstances loadFromLocalCache() throws Exception {
        List<net.arksea.dsf.store.Instance> list = LocalStore.load(serviceName);
        DSF.SvcInstances.Builder builder = DSF.SvcInstances.newBuilder()
            .setName(serviceName)
            .setSerialId("");
        for (net.arksea.dsf.store.Instance i : list){
            builder.addInstances(
                DSF.Instance.newBuilder()
                    .setAddr(i.getAddr())
                    .setPath(i.getPath())
                    .setOnline(true)
                    .build());
        }
        log.info("Load service list form local cache file succeed",serviceName);
        return builder.build();
    }
}
