package net.arksea.dsf.demo;

import java.io.Serializable;

/**
 *
 * Created by arksea on 2016/11/17.
 */
public class DataResult<TData> implements Serializable {
    public final String cacheName;
    public final String key;
    public final TData data;
    public final long expiredTime;
    public final int errorCode;
    public DataResult(String cacheName, String key, final long time, final TData data) {
        this.expiredTime = time;
        this.data = data;
        this.cacheName = cacheName;
        this.key = key;
        this.errorCode = 0;
    }
}
