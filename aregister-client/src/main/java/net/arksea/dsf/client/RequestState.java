package net.arksea.dsf.client;

import akka.actor.ActorRef;
import net.arksea.dsf.ServiceRequest;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class RequestState {
    public final ActorRef requester;
    public final long startTime;
    public final ServiceRequest request;
    public final Instance instance;

    public RequestState(ActorRef requester, long startTime, ServiceRequest request, Instance instance) {
        this.requester = requester;
        this.startTime = startTime;
        this.request = request;
        this.instance = instance;
    }
}
