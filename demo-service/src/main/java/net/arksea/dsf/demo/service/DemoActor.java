package net.arksea.dsf.demo.service;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.dsf.demo.DemoRequest1;
import net.arksea.dsf.demo.DemoResponse1;
import net.arksea.dsf.service.ServiceRequest;
import net.arksea.dsf.service.ServiceResponse;
import net.arksea.zipkin.akka.ActorTracingFactory;
import net.arksea.zipkin.akka.IActorTracing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/4/18.
 */
public class DemoActor extends AbstractActor {

    private final Logger log = LogManager.getLogger(DemoActor.class);
    private final int port;
    private IActorTracing tracing;
    private long start = System.currentTimeMillis();

    public DemoActor(int port) {
        this.port = port;
        tracing = ActorTracingFactory.create(self(), port);
    }

    public static Props props(int port) {
        return Props.create(new Creator<DemoActor>() {
            @Override
            public DemoActor create() throws Exception {
                return new DemoActor(port);
            }
        });
    }
    @Override
    public Receive createReceive() {
        return tracing.receiveBuilder()
            .match(ServiceRequest.class, this::onRequest)
            .match(String.class, this::onMessage)
            .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        log.info("DemoActor preStart()");
//        if (port == 8772) {
//            context().system().scheduler().scheduleOnce(Duration.create(80, TimeUnit.SECONDS),
//                self(), "offline", context().dispatcher(), self());
//            context().system().scheduler().scheduleOnce(Duration.create(180, TimeUnit.SECONDS),
//                self(), "online", context().dispatcher(), self());
//        }
    }

    @Override
    public void postStop() throws Exception {
        log.info("DemoActor postStop()");
        super.postStop();
    }

    private void onRequest(ServiceRequest msg) {
        if (msg.message instanceof DemoRequest1) {
            long time = System.currentTimeMillis() - start;
            if (time > 180_000 && time < 480_000) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                }
            }
            DemoRequest1 request = (DemoRequest1) msg.message;
//            log.info("onRequest: {}, online: {}", request.msg, online);
            if (port == 8772) {
                if (online) {
                    DemoResponse1 resule = new DemoResponse1(0, "received: " + request.msg);
                    ServiceResponse response = new ServiceResponse(resule, msg);
                    tracing.tell(sender(), response, self());
                }
            } else {
                DemoResponse1 resule = new DemoResponse1(0, "received: " + request.msg);
                ServiceResponse response = new ServiceResponse(resule, msg);
                tracing.tell(sender(), response, self());
            }
        }
    }

    boolean online = true;
    private void onMessage(String msg) {
        switch (msg) {
            case "online":
                log.info("onMessage: {}", msg);
                online = true;
                break;
            case "offline":
                log.info("onMessage: {}", msg);
                online = false;
                break;
            default:
                break;
        }
    }
}
