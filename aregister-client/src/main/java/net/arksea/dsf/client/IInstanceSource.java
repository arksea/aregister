package net.arksea.dsf.client;

import akka.actor.ActorRef;
import net.arksea.dsf.DSF;

/**
 *
 * Created by xiaohaixing on 2018/5/14.
 */
public interface IInstanceSource {
    DSF.SvcInstances getSvcInstances() throws Exception;
    default void subscribe(ActorRef subscriber) {
        //do nothing
    };
    default void unsubscribe(ActorRef subscriber) {
        //do nothing
    };
}
