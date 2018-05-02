package net.arksea.dsf.store;

/**
 *
 * Created by xiaohaixing on 2018/4/19.
 */
public class Instance implements Comparable {
    private String name;
    private String addr;
    private String path;

    public Instance(String name, String addr, String path) {
        this.name = name;
        this.addr = addr;
        this.path = path;
    }

    private Instance() {}

    public String getName() {
        return name;
    }

    public String getAddr() {
        return addr;
    }

    public String getPath() {
        return path;
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
            return i.getAddr().equals(addr);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return addr.hashCode();
    }
}
