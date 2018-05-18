package net.arksea.dsf.service;

import akka.actor.ActorRef;
import akka.routing.ConsistentHashingRouter;

import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public class ServiceRequest implements ConsistentHashingRouter.ConsistentHashable {
    public final String reqid;
    public final Object message;
    final ActorRef sender;
    public ServiceRequest(Object message, ActorRef sender) {
        this.message = message;
        this. sender = sender;
        this.reqid = UUID.randomUUID().toString();
    }
    public ServiceRequest(Object message, String reqid, ActorRef sender) {
        this.message = message;
        this.sender = sender;
        if (reqid == null) {
            this.reqid = UUID.randomUUID().toString();
        } else {
            this.reqid = reqid;
        }
    }

    @Override
    public Object consistentHashKey() {
        if (message instanceof ConsistentHashingRouter.ConsistentHashable) {
            ConsistentHashingRouter.ConsistentHashable m = (ConsistentHashingRouter.ConsistentHashable) message;
            return m.consistentHashKey();
        } else {
            return reqid;
        }
    }
}