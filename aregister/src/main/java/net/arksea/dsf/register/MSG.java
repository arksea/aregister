package net.arksea.dsf.register;

import akka.actor.ActorRef;

/**
 *
 * Created by xiaohaixing on 2018/4/18.
 */
public class MSG {
    public static class SendToAll {
        public final Object msg;
        public SendToAll(Object msg) {
            this.msg = msg;
        }
    }
    public static class StopServiceActor {
        public final ActorRef actorRef;
        public final String serviceName;

        public StopServiceActor(ActorRef actorRef, String serviceName) {
            this.actorRef = actorRef;
            this.serviceName = serviceName;
        }
    }

}
