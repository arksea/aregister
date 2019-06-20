package net.arksea.dsf.register.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.dsf.store.IRegisterStore;
import net.arksea.dsf.store.Instance;
import net.arksea.dsf.store.RegisterStoreException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;

/**
 *
 * Created by xiaohaixing on 2018/4/19.
 */
public class RedisRegister implements IRegisterStore {
    private final Logger log = LogManager.getLogger(RedisRegister.class);
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisRegister(String host, int port, int timeout, String password) {
        GenericObjectPoolConfig cfg = new GenericObjectPoolConfig();
        cfg.setMinIdle(1);
        cfg.setMaxIdle(5);
        cfg.setMaxTotal(10);
        cfg.setTestOnBorrow(true);
        cfg.setTestWhileIdle(true);
        jedisPool = StringUtils.isEmpty(password)?new JedisPool(cfg,host,port,timeout):
                                                  new JedisPool(cfg, host, port, timeout, password);
    }

    @Override
    public List<Instance> getServiceInstances(String name) {
        try(Jedis jedis = getJedis()) {
            String key = "dsf:"+name+":inst";
            Map<String,String> map = jedis.hgetAll(key);
            List<Instance> ret = new LinkedList<>();
            map.forEach((addr,json) -> {
                try {
                    Instance i = objectMapper.readValue(json, Instance.class);
                    ret.add(i);
                } catch (Exception ex) {
                    log.warn("Instance格式错误,key={},value={}",key,json, ex);
                }
            });
            return ret;
        }
    }

    @Override
    public void addServiceInstance(String name, Instance instance) {
        try(Jedis jedis = getJedis()) {
            String json = objectMapper.writeValueAsString(instance);
            String key = "dsf:"+name+":inst";
            jedis.hset(key, instance.getAddr(), json);
            //此处没有做事务，所以将版本ID更新放后面
            String status = jedis.set("dsf:"+name+":ver", UUID.randomUUID().toString());
            if (!"OK".equals(status)) {
                throw new RegisterStoreException();
            }
        } catch (JsonProcessingException e) {
            throw new RegisterStoreException("serialize Instance failed", e);
        }
    }

    @Override
    public void delServiceInstance(String name, String addr) {
        try(Jedis jedis = getJedis()) {
            String key = "dsf:"+name+":inst";
            jedis.hdel(key, addr);
            //此处没有做事务，所以将版本ID更新放后面
            String status = jedis.set("dsf:"+name+":ver", UUID.randomUUID().toString());
            if (!"OK".equals(status)) {
                throw new RegisterStoreException();
            }
        }
    }

    @Override
    public boolean serviceExists(String name) {
        try(Jedis jedis = getJedis()) {
            String key = "dsf:"+name+":inst";
            return jedis.exists(key);
        }
    }

    @Override
    public void delService(String name) {
        try(Jedis jedis = getJedis()) {
            String key = "dsf:"+name+":inst";
            jedis.del(key);
            String status = jedis.set("dsf:"+name+":ver", UUID.randomUUID().toString());
            if (!"OK".equals(status)) {
                throw new RegisterStoreException();
            }
        }
    }

    @Override
    public String getVersionID(String name) {
        try(Jedis jedis = getJedis()) {
            String key = "dsf:"+name+":ver";
            return jedis.get(key);
        }
    }

    private Jedis getJedis() {
        return jedisPool.getResource();
    }
}
