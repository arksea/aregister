package net.arksea.dsf.demo;

import java.io.Serializable;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class DemoResponse1 implements Serializable {
    public final int status;
    public final String msg;
    public DemoResponse1(int status, String msg) {
        this.msg = msg;
        this.status = status;
    }
}
