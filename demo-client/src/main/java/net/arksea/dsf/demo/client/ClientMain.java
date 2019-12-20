package net.arksea.dsf.demo.client;

import akka.dispatch.OnComplete;
import net.arksea.dsf.client.Client;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.codes.ProtocolBufferCodes;
import net.arksea.dsf.demo.DEMO;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.zipkin.akka.demo.TracingConfigImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/04/17.
 */
public final class ClientMain {
    private static final Logger logger = LogManager.getLogger(ClientMain.class);
    private ClientMain() {};
    private static Client client;
    public static String reqid() {
        return UUID.randomUUID().toString().replace("-","");
    }
    /**
     * @param args command line args
     */
    public static void main(final String[] args) {
        try {
            logger.info("Start DEMO Client");
            String serviceName = "net.arksea.dsf.DemoService-v2";
            LinkedList<String> addrs = new LinkedList<>();
            addrs.add("127.0.0.1:6501");
            RegisterClient register = new RegisterClient("TestClient",addrs,new TracingConfigImpl());
            //ICodes codes = new ProtocolBufferCodes(DEMO.getDescriptor());
            ICodes codes = new JavaSerializeCodes();
            client = register.subscribe(serviceName, codes);
            //----------------------------------------------------------------------------------------------------------
//            int N = 10;
//            DEMO.DemoRequest1[] msgArray = new DEMO.DemoRequest1[N];
//            for (int i = 0; i < N; ++i) {
//                msgArray[i] = DEMO.DemoRequest1.newBuilder().setMsg("hello" + (200+i)).setConsistentHashKey(i).build();
//            }
//            Future<String> f = client.request(msgArray, 10000).mapTo(classTag(String.class));
//            f.onComplete(
//                new OnComplete<String>() {
//                    @Override
//                    public void onComplete(Throwable failure, String ret) throws Throwable {
//                        if (failure == null) {
//                            logger.info("result message: {}", ret);
//                        } else {
//                            logger.warn("failed", failure);
//                        }
//                    }
//                }, client.system.dispatcher()
//            );

            //----------------------------------------------------------------------------------------------------------
//            Future<DEMO.DemoResponse1[]> f = client.request(msgArray, 10000).mapTo(classTag(DEMO.DemoResponse1[].class));
//            f.onComplete(
//                new OnComplete<DEMO.DemoResponse1[]>() {
//                    @Override
//                    public void onComplete(Throwable failure, DEMO.DemoResponse1[] ret) throws Throwable {
//                        if (failure == null) {
//                            logger.info("result message: {}", retToString(ret));
//                        } else {
//                            logger.warn("failed", failure);
//                        }
//                    }
//                }, client.system.dispatcher()
//            );

            //----------------------------------------------------------------------------------------------------------
            for (int i = 200; i < 201; ++i) {
                DEMO.DemoRequest1 msg = DEMO.DemoRequest1.newBuilder().setMsg("hello" + i).setConsistentHashKey(i).build();
                Future<DEMO.DataResult> f = client.request(msg, 10000).mapTo(classTag(DEMO.DataResult.class));
                f.onComplete(
                    new OnComplete<DEMO.DataResult>() {
                        @Override
                        public void onComplete(Throwable failure, DEMO.DataResult ret) throws Throwable {
                            if (failure == null) {
                                client.trace(ret, it -> {
                                    logger.info("result message: {}", it.getKey());
                                });
                            } else {
                                logger.warn("failed", failure);
                            }
                        }
                    }, client.system.dispatcher()
                );
                Thread.sleep(10);
            }


//            for (int n=0; n<5; ++n) {
//                for (int i = 100; i < 110; ++i) {
//                    String msg = "world" + i;
//                    Future<String> f = client.request(msg, 10000).mapTo(classTag(String.class));
//                    f.onComplete(
//                        new OnComplete<String>() {
//                            @Override
//                            public void onComplete(Throwable failure, String ret) throws Throwable {
//                                if (failure == null) {
//                                    logger.info("result message: {}", ret);
//                                } else {
//                                    logger.warn("failed", failure);
//                                }
//                            }
//                        }, client.system.dispatcher()
//                    );
//                    Thread.sleep(10);
//                }
//            }
            //----------------------------------------------------------------------------------------------------------
//            int N = 10;
//            List<DEMO.DemoRequest1> msg = new LinkedList<>();
//            for (int i = 0; i < N; ++i) {
//                msg.add(DEMO.DemoRequest1.newBuilder().setMsg("hello" + (200+i)).setConsistentHashKey(i).build());
//            }
//            Future<String> f = client.request(reqid(), msg, 10000).mapTo(classTag(String.class));
//            f.onComplete(
//                new OnComplete<String>() {
//                    @Override
//                    public void onComplete(Throwable failure, String ret) throws Throwable {
//                        if (failure == null) {
//                            logger.info("result message: {}", ret);
//                        } else {
//                            logger.warn("failed", failure);
//                        }
//                    }
//                }, client.system.dispatcher()
//            );


            Thread.sleep(10000);
            Await.result(client.system.terminate(), Duration.apply(10, TimeUnit.SECONDS));
        } catch (Exception ex) {
            logger.error("DEMO Client failed", ex);
        }
    }

    static String retToString(DEMO.DemoResponse1[] arr) {
        StringBuilder sb = new StringBuilder();
        for (DEMO.DemoResponse1 i: arr) {
            sb.append(i.getMsg()).append(",");
        }
        return sb.toString();
    }

    static long __lastLogTime;
    private static void complete(DEMO.DemoResponse1 ret) {
        //if (System.currentTimeMillis() - __lastLogTime > 10_000) {
        //    __lastLogTime = System.currentTimeMillis();
            logger.info("result message: {}", ret.getMsg());
        //}
    }
}
