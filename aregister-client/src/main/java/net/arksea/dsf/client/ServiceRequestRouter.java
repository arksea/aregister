package net.arksea.dsf.client;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.route.IRouteStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class ServiceRequestRouter extends RequestRouter {
    private static final Logger log = LogManager.getLogger(ServiceRequestRouter.class);

    private final Map<String, RequestState> requests = new HashMap<>();
    protected ServiceRequestRouter(String serviceName, IInstanceSource instanceSource, IRouteStrategy routeStrategy, ISwitchCondition condition) {
        super(serviceName, instanceSource, routeStrategy, condition, false);

    }

    public static Props props(String serviceName, IInstanceSource instanceSource, IRouteStrategy routeStrategy, ISwitchCondition condition) {
        return Props.create(ServiceRequestRouter.class, () -> new ServiceRequestRouter(serviceName,instanceSource,routeStrategy, condition));
    }

    @Override
    public Receive createReceive() {
        return createReceiveBuilder()
            .match(DSF.ServiceRequest.class, this::handleServiceRequest)
            .match(DSF.ServiceResponse.class,this::handleServiceResponse)
            .match(DSF.RegService.class,     this::handleRegService)
            .match(DSF.UnregService.class,   this::handleUnregService)
            .match(DSF.SvcInstances.class,   this::handleSvcInstances)
            .build();
    }

    //------------------------------------------------------------------------------------
    private void handleServiceRequest(DSF.ServiceRequest msg) {
        log.trace("handleServiceRequest({},{},{})", msg.getTypeName(), msg.getRequestId(), msg.getOneway());
        long startTime = System.currentTimeMillis();
        Optional<Instance> op = getInstance();
        final ActorRef requester = sender();
        if (op.isPresent()) {
            Instance instance = op.get();
            try {
                log.trace("service instance: {}", instance.path);
                ActorSelection service = context().actorSelection(instance.path);
                service.tell(msg, self());
                if (!msg.getOneway()) {
                    RequestState state = new RequestState(requester, startTime, msg, instance);
                    requests.put(msg.getRequestId(), state);
                }
            } catch (Exception ex) {
                onRequestFailed(instance, System.currentTimeMillis() - startTime);
                throw ex;
            }
        } else {
            requester.tell(new NoUseableService(serviceName), self());
        }
    }
    //------------------------------------------------------------------------------------
    private void handleServiceResponse(DSF.ServiceResponse msg) {
        log.trace("handleServiceResponse({},{})", msg.getTypeName(), msg.getRequestId());
        RequestState state = requests.remove(msg.getRequestId());
        if (state == null) {
            log.warn("not fond the request state : {}", msg.getRequestId());
        } else {
            state.requester.forward(msg, context());
            long time = System.currentTimeMillis() - state.startTime;
            if (time > getReuqestTimeout() || !msg.getSucceed()) {
                onRequestFailed(state.instance, time);
            } else {
                onRequestSucceed(state.instance, time);
            }
        }
    }
    //------------------------------------------------------------------------------------
    private void handleRegService(DSF.RegService msg) {
        log.info("Service REG : {}@{}",msg.getName(),msg.getAddr());
        onAddInstance(new Instance(serviceName, msg.getAddr(), msg.getPath()));
    }
    //-------------------------------------------------------------------------------
    private void handleUnregService(DSF.UnregService msg) {
        log.info("Service UNREG : {}@{}",msg.getName(),msg.getAddr());
        onDelInstance(new Instance(serviceName, msg.getAddr(), null));
    }
    //------------------------------------------------------------------------------------
    private void handleSvcInstances(DSF.SvcInstances msg) {
        initInstances(msg);
    }

    protected void checkTimeoutRequest() {
        long now = System.currentTimeMillis();
        List<String> timeoutRequests = new LinkedList<>();
        for (Map.Entry<String, RequestState> e: requests.entrySet()) {
            if (now - e.getValue().startTime > getReuqestTimeout()) {
                timeoutRequests.add(e.getKey());
                Instance i = e.getValue().instance;
                onRequestFailed(i, getReuqestTimeout());
            }
        }
        timeoutRequests.forEach(requests::remove);
    }
}
