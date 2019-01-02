package net.arksea.dsf.demo;

import net.arksea.zipkin.akka.AbstractTraceableMessage;

import java.io.Serializable;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class DemoResponse1 extends AbstractTraceableMessage implements Serializable {
    public final int status;
    public final String msg;
    public DemoResponse1(int status, String msg) {
        this.msg = msg;
        this.status = status;
    }
}
