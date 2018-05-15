package net.arksea.dsf.service;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class RegisteredServiceAdaptor extends ServiceAdaptor {
    private static final Logger logger = LogManager.getLogger(RegisteredServiceAdaptor.class);
    private final RegisterClient register;
    private final String serviceName;
    private final String serviceAddr;
    private final String servicePath;
    protected RegisteredServiceAdaptor(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register) {
        super(service, codes);
        this.serviceName = serviceName;
        this.register = register;
        Address address = Address.apply("akka.tcp",context().system().name(),host, port);
        serviceAddr = host + ":" + port;
        servicePath = self().path().toStringWithAddress(address);
        logger.info("Create Service Adaptor: addr={}, path={}", serviceAddr, servicePath);
    }


    public static Props props(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register) {
        return Props.create(new Creator<RegisteredServiceAdaptor>() {
            @Override
            public RegisteredServiceAdaptor create() throws Exception {
                return new RegisteredServiceAdaptor(serviceName, host, port, service, codes, register);
            }
        });
    }

    public static Props props(String serviceName, String host, int port, ActorRef service, RegisterClient register) {
        return Props.create(new Creator<RegisteredServiceAdaptor>() {
            @Override
            public RegisteredServiceAdaptor create() throws Exception {
                return new RegisteredServiceAdaptor(serviceName, host, port, service, new JavaSerializeCodes(), register);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return createReceiveBuilder()
            .match(DelayRegister.class,     this::handleDelayRegister)
            .match(Unregister.class,        this::handleUnregister)
            .build();
    }

    @Override
    public void preStart() throws Exception {
        context().system().scheduler().scheduleOnce(Duration.create(3, TimeUnit.SECONDS),
            self(),new DelayRegister(),context().dispatcher(),self());
    }

    @Override
    public void postStop() throws Exception {
        try {
            Future f = register.unregisterInfo(serviceName, serviceAddr, 10000);
            Await.result(f, Duration.create(10, TimeUnit.SECONDS));
        } catch (Exception ex) {
            logger.warn("Unregister service timeout: {}@{}", serviceName, serviceAddr, ex);
        }
    }
    //------------------------------------------------------------------------------------

    class DelayRegister {}
    private void handleDelayRegister(DelayRegister msg) {
        register.registerInfo(serviceName, serviceAddr, servicePath);
    }
    //------------------------------------------------------------------------------------
    public static class Unregister {}
    private void handleUnregister(Unregister msg) {
        register.unregisterInfo(serviceName, serviceAddr, sender());
    }
}
