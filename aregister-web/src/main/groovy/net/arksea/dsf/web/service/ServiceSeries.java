package net.arksea.dsf.web.service;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/6/13.
 */
public class ServiceSeries {
    private String name;
    private List<ServiceVersion> versions;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ServiceVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<ServiceVersion> versions) {
        this.versions = versions;
    }
}
