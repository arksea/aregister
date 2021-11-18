package net.arksea.dsf.demo.service;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.protobuf.ByteString;
import net.arksea.dsf.demo.DEMO;
import net.arksea.dsf.service.ServiceRequest;
import net.arksea.dsf.service.ServiceResponse;
import net.arksea.zipkin.akka.ActorTracingFactory;
import net.arksea.zipkin.akka.IActorTracing;
import net.arksea.zipkin.akka.demo.TracingConfigImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/4/18.
 */
public class DemoActor extends AbstractActor {

    private final Logger log = LogManager.getLogger(DemoActor.class);
    private IActorTracing tracing;

    public DemoActor(int port) {
        tracing = ActorTracingFactory.create(new TracingConfigImpl(), "DemoActor", "localhost", port);
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
            .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        log.info("DemoActor preStart()");
    }

    @Override
    public void postStop() throws Exception {
        log.info("DemoActor postStop()");
        super.postStop();
    }

    private void onRequest(ServiceRequest msg) {
        System.out.println(msg.message.getClass());
        if (msg.message instanceof DEMO.DemoRequest1) {
            DEMO.DemoRequest1 request = (DEMO.DemoRequest1) msg.message;
//模拟响应时间变长，测试流控策略是否生效
//            long time = System.currentTimeMillis() - start;
//            if (time > 90_000 && time <= 360_000) {
//                try {
//                    Thread.sleep(10);
//                } catch (InterruptedException e) {
//                }
//            } else if (time > 360_000 && time < 480_000) {
//                try {
//                    Thread.sleep(5);
//                } catch (InterruptedException e) {
//                }
//            }
            //流控测试时记得注释log，以免产生巨量日志
            log.info("onRequest: {}, name={}", request.getMsg(), self().path().name());
            String str = "     {\n" +
                "     \"Date\": \"2018-06-01T18:00:00+08:00\",\n" +
                "     \"EpochDate\": 1527847200,\n" +
                "     \"Index\": 70,\n" +
                "     \"ParticulateMatter2_5\": 22,\n" +
                "     \"ParticulateMatter10\": 46,\n" +
                "     \"Ozone\": 176,\n" +
                "     \"CarbonMonoxide\": 0,\n" +
                "     \"NitrogenMonoxide\": null,\n" +
                "     \"NitrogenDioxide\": 16,\n" +
                "     \"SulfurDioxide\": 2,\n" +
                "     \"Lead\": null,\n" +
                "     \"Source\": \"MEP\"\n" +
                "     }";
            //DEMO.DataResult resule = DEMO.nse1.newBuilder().setStatus(0).setMsg("received: " + request.getMsg()).build();
            DEMO.DataResult resule = DEMO.DataResult.newBuilder()
                .setKey(request.getMsg())
                .setExpiredTime(System.currentTimeMillis() + 3600_000)
                .setErrorCode(0)
                .setPayload(ByteString.copyFrom(str, Charset.forName("UTF-8")))
                .setSerialize(5)
                .setTypeName("JAVASTRING")
                .build();
            ServiceResponse response = new ServiceResponse(resule, msg);
            tracing.tell(sender(), response, self());
        } else if (msg.message instanceof Integer[]) {
            String str = toString((Integer[])msg.message);
            log.info("onMessage: {}, name={}", str, self().path().name());
            ServiceResponse response = new ServiceResponse("received "+str, msg);
            tracing.tell(sender(), response, self());
        }  else if (msg.message instanceof List) {
            String str = toString((List<String>)msg.message);
            log.info("onMessage: {}, name={}", str, self().path().name());
            ServiceResponse response = new ServiceResponse("received "+str, msg);
            tracing.tell(sender(), response, self());
        }  else if (msg.message instanceof DEMO.DemoRequest1[]) {
            DEMO.DemoRequest1[] reqArr = (DEMO.DemoRequest1[])msg.message;
            String str = toString(reqArr);
            log.info("onMessage: {}, name={}", str, self().path().name());
            DEMO.DemoResponse1[] arr = new DEMO.DemoResponse1[reqArr.length];
            for (int i=0;i<reqArr.length;++i) {
                arr[i] = DEMO.DemoResponse1.newBuilder().setMsg(reqArr[i].getMsg()).build();
            }
            ServiceResponse response = new ServiceResponse(arr, msg);
            tracing.tell(sender(), response, self());
        } else {
            log.info("onMessage: {}, name={}", msg.message, self().path().name());
            ServiceResponse response = new ServiceResponse("received "+msg.message, msg);
            tracing.tell(sender(), response, self());
        }

    }

    String toString(Integer[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i: arr) {
            sb.append(i).append(",");
        }
        return sb.toString();
    }
    String toString(List<String> arr) {
        StringBuilder sb = new StringBuilder();
        for (String i: arr) {
            sb.append(i).append(",");
        }
        return sb.toString();
    }

    String toString(DEMO.DemoRequest1[] arr) {
        StringBuilder sb = new StringBuilder();
        for (DEMO.DemoRequest1 i: arr) {
            sb.append(i.getMsg()).append(",");
        }
        return sb.toString();
    }
}
