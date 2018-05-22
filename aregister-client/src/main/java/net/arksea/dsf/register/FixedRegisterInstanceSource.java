package net.arksea.dsf.register;

import net.arksea.dsf.DSF;
import net.arksea.dsf.client.IInstanceSource;

import java.util.List;

/**
 * 指定地址的实例源
 * Created by xiaohaixing on 2018/5/14.
 */
public class FixedRegisterInstanceSource implements IInstanceSource {

    protected final String serviceName;
    protected final List<String> registerAddrs;

    public FixedRegisterInstanceSource(List<String> registerAddrs) {
        this.serviceName = "dsfRegister";
        this.registerAddrs = registerAddrs;
    }

    @Override
    public DSF.SvcInstances getSvcInstances() {
        DSF.SvcInstances.Builder builder = DSF.SvcInstances.newBuilder()
            .setName(serviceName)
            .setSerialId("");
        for (String addr : registerAddrs){
            String path = "akka.tcp://DsfCluster@"+addr+"/user/dsfRegister";
            builder.addInstances(
                DSF.Instance.newBuilder()
                    .setAddr(addr)
                    .setPath(path)
                    .setOnline(true)
                    .build());
        }
        return builder.build();
    }
}
