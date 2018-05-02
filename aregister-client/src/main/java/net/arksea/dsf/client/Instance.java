package net.arksea.dsf.client;

/**
 *
 * Created by xiaohaixing on 2018/4/19.
 */
public class Instance implements Comparable {
    public final String name;
    public final String addr;
    public final String path;
    public InstanceStatus status;
    private long checkCount;

    public Instance(String name, String addr, String path) {
        this.name = name;
        this.addr = addr;
        this.path = path;
        this.status = InstanceStatus.ONLINE;
    }

    public Instance(String name, String addr, String path, InstanceStatus status) {
        this.name = name;
        this.addr = addr;
        this.path = path;
        this.status = status;
    }

    @Override
    public int compareTo(Object o) {
        Instance other = (Instance) o;
        return this.addr.compareTo(other.addr);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof Instance) {
            Instance i = (Instance) o;
            return i.addr.equals(addr);
        } else {
            return false;
        }
    }

    public boolean check() {
        //当为UP状态时使用1/10的流量进行尝试性访问
        return status == InstanceStatus.ONLINE ||
               status == InstanceStatus.UP && checkCount++ % 10 == 0;
    }

    @Override
    public int hashCode() {
        return addr.hashCode();
    }
}
