package net.arksea.dsf.register;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * Created by xiaohaixing on 2018/5/22.
 */ //-------------------------------------------------------------------------------
public class InstanceInfo {
    public final String name;
    public final String addr;
    public final String path;
    public final long registerTime;

    private long lastOfflineTime;
    private long lastOnlineTime;
    private long unregisterTime;
    private boolean online;
    private boolean unregistered;
    private static final Logger logger = LogManager.getLogger(InstanceInfo.class);

    public InstanceInfo(String name, String addr, String path, boolean online) {
        this.name = name;
        this.addr = addr;
        this.path = path;
        this.online = online;
        this.unregistered = false;
        this.registerTime = System.currentTimeMillis();
        this.lastOfflineTime = System.currentTimeMillis();
    }

    public boolean isOnline() {
        return online;
    }

    public boolean isUnregistered() {
        return unregistered;
    }

    public void unregister() {
        this.unregistered = true;
        this.unregisterTime = System.currentTimeMillis();
    }

    public long getLastOfflineTime() {
        return lastOfflineTime;
    }

    public long getUnregisterTime() {
        return unregisterTime;
    }

    public void setOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            if (online) {
                lastOnlineTime = System.currentTimeMillis();
                logger.info("Service ONLINE : {}@{}", name, addr);
            } else {
                lastOfflineTime = System.currentTimeMillis();
                logger.warn("Service OFFLINE : {}@{}", name, addr);
            }
        }
    }

    public long getLastOnlineTime() {
        return lastOnlineTime;
    }
}
