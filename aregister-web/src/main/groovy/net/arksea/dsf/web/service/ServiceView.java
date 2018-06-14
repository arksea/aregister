package net.arksea.dsf.web.service;

import akka.japi.tuple.Tuple3;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.dsf.DSF;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 *
 * Created by xiaohaixing on 2018/6/14.
 */
public class ServiceView {
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static String renderServiceTree(DSF.ServiceList list) throws JsonProcessingException {
        List<ServiceNamespace> tree = parseServcieList(list);
        return objectMapper.writeValueAsString(tree);
    }

    private static List<ServiceNamespace> parseServcieList(DSF.ServiceList list) {
        Map<String, Map<String,List<ServiceVersion>>> namespaceMap = new TreeMap<>();
        for (String svc : list.getItemsList()) {
            Tuple3<String, String, String> t = parseServiceName(svc);
            Map<String, List<ServiceVersion>> namespace = namespaceMap.computeIfAbsent(t.t1(), k -> new TreeMap<>());
            List<ServiceVersion> series = namespace.computeIfAbsent(t.t2(), k -> new LinkedList<>());
            ServiceVersion version = new ServiceVersion();
            version.setVersion(t.t3());
            version.setRegname(svc);
            series.add(version);
        }
        List<ServiceNamespace> namespaceList = new LinkedList<>();
        namespaceMap.forEach((k,v) -> {
            ServiceNamespace n = new ServiceNamespace();
            n.setNamespace(k);
            List<ServiceSeries> serviceList = new LinkedList<>();
            v.forEach((k2, v2) -> {
                ServiceSeries ss = new ServiceSeries();
                ss.setName(k2);
                ss.setVersions(v2);
                serviceList.add(ss);
            });
            n.setServiceList(serviceList);
            namespaceList.add(n);
        });
        return namespaceList;
    }

    private static Tuple3<String,String,String> parseServiceName(String fullname) {
        String[] strs = StringUtils.split(fullname, '.');
        int n = strs.length;
        StringBuilder namespace = new StringBuilder();
        int m = n - 1;
        if (n > 1) {
            for (int i = 0; i<m; ++i) {
                if (i > 0) {
                    namespace.append(".");
                }
                namespace.append(strs[i]);
            }
        } else {
            namespace.append("/");
        }
        String last = strs[m];
        String[] list = StringUtils.split(last, "-", 2);
        String name = list[0];
        String ver = "default";
        if (list.length > 1) {
            ver = list[1];
        }
        return Tuple3.apply(namespace.toString(),name,ver);
    }
}
