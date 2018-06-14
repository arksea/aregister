package net.arksea.dsf.web.service;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/6/13.
 */
public class ServiceNamespace {
    private String namespace;
    private List<ServiceSeries> serviceList;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public List<ServiceSeries> getServiceList() {
        return serviceList;
    }

    public void setServiceList(List<ServiceSeries> serviceList) {
        this.serviceList = serviceList;
    }
}
