package net.arksea.dsf.register;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;
import static akka.japi.Util.classTag;

/**
 * 从集群获取所有服务器地址
 * Created by xiaohaixing on 2018/5/14.
 */
public class RegisterInstanceSource extends FixedRegisterInstanceSource {
    private static final Logger log = LogManager.getLogger(RegisterInstanceSource.class);
    private final ActorSystem registerClientSystem;

    public RegisterInstanceSource(List<String> registerAddrs, ActorSystem registerClientSystem) {
        super(registerAddrs);
        this.registerClientSystem = registerClientSystem;
    }

    @Override
    public DSF.SvcInstances getSvcInstances() {
        for (String addr: registerAddrs) {
            String path = "akka.tcp://DsfCluster@"+addr+"/user/dsfRegister";
            ActorSelection sel = registerClientSystem.actorSelection(path);
            Future<DSF.SvcInstances> f = Patterns.ask(sel, DSF.GetRegisterInstances.getDefaultInstance(), 5000)
                .mapTo(classTag(DSF.SvcInstances.class));
            try {
                DSF.SvcInstances ret = Await.result(f, Duration.apply(5, TimeUnit.SECONDS));
                log.info("Get register server addresss succeed, count={}",ret.getInstancesCount());
                return ret;
            } catch (Exception e) {
                log.warn("Get register server addresss failed",e);
            }
        }
        return super.getSvcInstances();
    }
}
