package net.arksea.dsf.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 *
 * Created by xiaohaixing on 2017/7/21.
 */
public class FileConfigPersistence implements IConfigPersistence {
    private static Logger logger = LogManager.getLogger(FileConfigPersistence.class);
    private final File file;
    private final Map<String,String> configMap = new ConcurrentHashMap<>();

    public FileConfigPersistence(String filePath)  {
        file = new File(filePath);
        File dir = file.getParentFile();
        if (file.exists()) {
            load();
        } else if (!dir.exists()){
            try {
                Files.createDirectories(dir.toPath());
            } catch (IOException ex) {
                logger.warn("创建配置文件目录失败: {}", dir.getPath(), ex);
            }
        }
    }

    public String read(String key) {
        return configMap.get(key);
    }

    public void update(String key, String value) {
        String old = configMap.put(key, value);
        if (old == null || !old.equals(value)) {
            save();
        }
    }

    private void load() {
        try {
            List<String> list = Files.readAllLines(file.toPath());
            String key = null;
            StringBuilder value = new StringBuilder();
            for (String str : list) {
                int n = str.indexOf("->");
                if (n > 0) {
                    if (key != null) {
                        configMap.put(key, value.toString());
                        value.setLength(0);
                    }
                    key = str.substring(0, n).trim();
                    String v = str.substring(n+2).trim();
                    if (v.length() > 0) {
                        value.append(v);
                    }
                } else {
                    value.append('\n').append(str);
                }
            }
            if (key != null) {
                configMap.put(key, value.toString());
            }
        } catch (IOException ex) {
            logger.warn("读取配置文件失败: {}", file.getPath(), ex);
        }
    }

    private void save() {
        synchronized(this) {
            try {
                StringBuilder sb = new StringBuilder();
                List<String> keys = new LinkedList<String>();
                keys.addAll(configMap.keySet());
                java.util.Collections.sort(keys);
                for (String key : keys) {
                    String value = configMap.get(key);
                    sb.append(key).append(" -> ").append(value);
                    if (!value.endsWith("\n")) {
                        sb.append("\n");
                    }
                }
                Files.write(file.toPath(), sb.toString().getBytes("UTF-8"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch (IOException ex) {
                logger.warn("保存配置文件失败: {}", file.getPath(), ex);
            }
        }
    }
}
