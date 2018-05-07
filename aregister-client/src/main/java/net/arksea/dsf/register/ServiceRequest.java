package net.arksea.dsf.register;

import akka.actor.ActorRef;

import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public class ServiceRequest {
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
}