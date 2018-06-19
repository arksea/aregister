package net.arksea.dsf.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class InstanceQuality {
    private final Logger log = LogManager.getLogger(InstanceQuality.class);
    private static int MAX_HISTORY_COUNT = 15;   //保存历史数据的周期数
    private final ArrayList<Count> historyStat;  //每分钟保存一个历史值
    private long requestCount;
    private long respondTime;
    private long succeedCount;
    private  int lastHistoryIndex;
    private final String serviceName;

    public class Count {
        public final long requestCount;
        public final long respondTime;
        public final long succeedCount;

        public Count(long requestCount, long succeedCount, long respondTime) {
            this.requestCount = requestCount;
            this.respondTime = respondTime;
            this.succeedCount = succeedCount;
        }
    }

    public InstanceQuality(String serviceName) {
        this.serviceName = serviceName;
        historyStat = new ArrayList<>(MAX_HISTORY_COUNT);
        for (int i = 0; i< MAX_HISTORY_COUNT; ++i) {
            historyStat.add(new Count(0,0,0));
        }
    }

    public void failed(long time) {
        ++requestCount;
        this.respondTime += time;
    }

    public void succeed(long time) {
        //请求与响应的计数放在一起，是为了防止请求时间比较长的情况下，两次计数被分到两个周期中
        ++succeedCount;
        ++requestCount;
        this.respondTime += time;
    }

    /**
     * 保存当前统计数据
     */
    public void saveHistory() {
        ++lastHistoryIndex;
        if (lastHistoryIndex >= MAX_HISTORY_COUNT) {
            lastHistoryIndex = 0;
        }
        Count count = new Count(this.requestCount, this.succeedCount, this.respondTime);
        historyStat.set(lastHistoryIndex, count);
        log.trace("Quality Stat: service={},requestCount1M={},succeedRate1M={},succeedRate5M={}",
            serviceName, getRequestCount(1), getSucceedRate(1), getSucceedRate(5));
    }

    /**
     * 计算指定时间范围内的平均请求成功率
     * @param step 指定时间范围，具体时间与每个step调用的周期有关，
                   比如每分钟一个step，则step=5表示计算最近5分钟内的平均请求成功率
                   如果10秒钟一个setp，则step=5就表示计算最近50秒内的平均请求成功率
                   具体设置多大的周期，看希望调整实例上下线的灵敏度：
                   1、对于质量好的环境，可以将周期设置得短一些，以获得更加灵敏的响应；
                      而对于质量差的环境，则应该设置长的周期以避免系统不必要的震荡
                   2、对于QPS高的服务，因为参考数据点多，可以设置相对短的周期；
                      而对于QPS低的服务，太短的周期会降低这里统计数据的参考价值、引起误判
     * @return 0~1.0
     */
    public float getSucceedRate(int step) {
        int s = Math.min(MAX_HISTORY_COUNT -1, step);
        Count cOld = getStepsBefore(s);
        long n = this.succeedCount - cOld.succeedCount;
        long m = this.requestCount - cOld.requestCount;
        if (m == 0) {
            return 1.0f;
        } else {
            return 1.0f*n / m;
        }
    }

    /**
     * 计算指定时间范围内的平均请求响应时间
     */
    public long getMeanRespondTime(int step) {
        int s = Math.min(MAX_HISTORY_COUNT -1, step);
        Count cOld = getStepsBefore(s);
        long n = this.respondTime - cOld.respondTime;
        long m = this.requestCount - cOld.requestCount;
        if (m == 0) {
            return 0L;
        } else {
            return n / m;
        }
    }

    public long getRequestCount(int step) {
        int s = Math.min(MAX_HISTORY_COUNT -1, step);
        Count cOld = getStepsBefore(s);
        return this.requestCount - cOld.requestCount;
    }

    /**
     * 取N分钟前的统计数据
     * @param step
     * @return
     */
    public Count getStepsBefore(int step) {
        if (step > 0) {
            int index = lastHistoryIndex - step;
            if (index < 0) {
                index += MAX_HISTORY_COUNT;
            }
            return historyStat.get(index);
        } else {
            return new Count(this.requestCount, this.succeedCount, this.respondTime);
        }
    }

    public int getMaxHistoryCount() {
        return MAX_HISTORY_COUNT;
    }

    public List<Count> getCountHistory() {
        int i1 = lastHistoryIndex + 1;
        if (i1 >= MAX_HISTORY_COUNT) {
            i1 = 0;
        }
        int i2 = historyStat.size();
        List<Count> his = new LinkedList<>();
        his.addAll(historyStat.subList(i1, i2));
        his.addAll(historyStat.subList(0,i1));
        Count current = new Count(this.requestCount, this.succeedCount, this.respondTime);
        his.add(current);
        return his;
    }

}
