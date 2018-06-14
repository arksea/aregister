package net.arksea.dsf.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/5/8.
 */
public final class LocalStore {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private LocalStore() {}
    public static void save(String serviceName, Collection<Instance> instances) throws IOException {
        String fileName = "./config/" + serviceName + ".svc";
        File file = new File(fileName);
        File dir = file.getParentFile();
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        StringBuilder sb = new StringBuilder();
        for(Instance instance : instances) {
            String line = objectMapper.writeValueAsString(instance);
            sb.append(line).append("\n");
        }
        Files.write(file.toPath(), sb.toString().getBytes("UTF-8"),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE);
    }

    public static List<Instance> load(String serviceName) throws IOException {
        List<Instance> list = new LinkedList<>();
        String fileName = "./config/" + serviceName + ".svc";
        File file = new File(fileName);
        List<String> lines = Files.readAllLines(file.toPath());
        for (String line: lines){
            if (StringUtils.isNotBlank(line)) {
                net.arksea.dsf.store.Instance i = objectMapper.readValue(line, net.arksea.dsf.store.Instance.class);
                list.add(i);
            }
        }
        return list;
    }

    public static boolean serviceExists(String serviceName) throws IOException {
        String fileName = "./config/" + serviceName + ".svc";
        File file = new File(fileName);
        return file.exists();
    }
}
