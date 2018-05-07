package net.arksea.dsf.demo.service;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import net.arksea.dsf.demo.DemoRequest1;
import net.arksea.dsf.demo.DemoResponse1;
import net.arksea.dsf.service.ServiceRequest;
import net.arksea.dsf.service.ServiceResponse;
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

    public DemoActor(int port) {
        this.port = port;
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
        return ReceiveBuilder.create()
            .match(ServiceRequest.class, this::onRequest)
            .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        log.info("DemoActor preStart()");
        context().system().scheduler().scheduleOnce(Duration.create(30, TimeUnit.SECONDS),
        self(),"offline",context().dispatcher(),self());

        context().system().scheduler().scheduleOnce(Duration.create(130, TimeUnit.SECONDS),
            self(),"online",context().dispatcher(),self());
    }

    @Override
    public void postStop() throws Exception {
        log.info("DemoActor postStop()");
        super.postStop();
    }

    private void onRequest(ServiceRequest msg) {
        if (msg.message instanceof DemoRequest1) {
            DemoRequest1 request = (DemoRequest1) msg.message;
            log.info("onRequest: {}, online: {}", request.msg, online);
            if (port == 8772) {
                if (online) {
                    DemoResponse1 resule = new DemoResponse1(0, "received: " + request.msg);
                    ServiceResponse response = new ServiceResponse(resule, msg);
                    sender().tell(response, self());
                }
            } else {
                DemoResponse1 resule = new DemoResponse1(0, "received: " + request.msg);
                ServiceResponse response = new ServiceResponse(resule, msg);
                sender().tell(response, self());
            }
        } else if (msg.message instanceof  String){
            onMessage(msg);
        }
    }

    boolean online = true;
    private void onMessage(ServiceRequest request) {
        String msg = (String)request.message;
        switch (msg) {
            case "heartbeat":
                if (port == 8772) {
                    if (online) {
                        sender().tell(new ServiceResponse(msg,request), self());
                    }
                } else {
                    sender().tell(new ServiceResponse(msg,request), self());
                }
                break;
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
