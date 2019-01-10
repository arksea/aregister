package net.arksea.dsf.demo;

import net.arksea.zipkin.akka.AbstractTraceableMessage;

import java.io.Serializable;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class DemoRequest1 extends AbstractTraceableMessage implements Serializable {
    public final String msg;
    public final int index;
    public DemoRequest1(String msg, int index) {
        this.index = index;
        this.msg = msg;
    }
}
