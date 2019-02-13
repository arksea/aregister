package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import scala.concurrent.Future;
import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2019/2/13.
 */
public class RegisterManager {
    private RegisterClient client;
    public RegisterManager(RegisterClient client) {
        this.client = client;
    }

    public void subscribeAtRepertory(String serviceName, ActorRef subscriber) {
        client.actorRef.tell(DSF.SubService.newBuilder()
            .setService(serviceName)
            .setSubscriber(client.clientName)
            .build(), subscriber);
    }

    public void unsubscribeAtRepertory(String serviceName, ActorRef subscriber) {
        client.actorRef.tell(DSF.UnsubService.newBuilder()
            .setService(serviceName)
            .build(), subscriber);
    }

    /**
     * 直接在注册服务器上注销一个服务
     * @param serviceName  服务注册名
     * @param serviceAddr  host:port 格式的服务地址
     */
    public Future<Boolean> unregisterAtRepertory(String serviceName, String serviceAddr, long timeout) {
        return Patterns.ask(client.actorRef, new UnregLocalService(serviceName,serviceAddr), timeout)
            .mapTo(classTag(Boolean.class));
    }
    public void unregisterAtRepertory(String serviceName, String serviceAddr) {
        client.actorRef.tell(new UnregLocalService(serviceName, serviceAddr), ActorRef.noSender());
    }

    /**
     * 直接在注册服务器上注册一个服务（提供服务path）
     * @param serviceName
     * @param serviceAddr
     */
    public Future<Boolean> registerAtRepertory(String serviceName, String serviceAddr, String servicePath, long timeout) {
        return Patterns.ask(client.actorRef, new RegLocalService(serviceName, serviceAddr, servicePath), timeout)
            .mapTo(classTag(Boolean.class));
    }
    public void registerAtRepertory(String serviceName, String serviceAddr, String servicePath) {
        client.actorRef.tell(new RegLocalService(serviceName, serviceAddr, servicePath), ActorRef.noSender());
    }

    public Future<DSF.ServiceList> getServiceList(long timeout) {
        return Patterns.ask(client.actorRef, DSF.GetServiceList.getDefaultInstance(), timeout)
            .mapTo(classTag(DSF.ServiceList.class));
    }

    public Future<DSF.Service> getService(String serviceName, long timeout) {
        DSF.GetService get = DSF.GetService.newBuilder().setName(serviceName).build();
        return Patterns.ask(client.actorRef, get, timeout).mapTo(classTag(DSF.Service.class));
    }

    /**
     * 获取指定服务的实例列表
     * @param serviceName 服务名
     * @param timeout 超时时间（毫秒）
     * @return 实例列表
     */
    public Future<DSF.SvcInstances> getServiceInstances(String serviceName, long timeout) {
        DSF.GetSvcInstances get = DSF.GetSvcInstances.newBuilder()
            .setName(serviceName)
            .build();
        return Patterns.ask(client.actorRef, get, timeout).mapTo(classTag(DSF.SvcInstances.class));
    }

}
