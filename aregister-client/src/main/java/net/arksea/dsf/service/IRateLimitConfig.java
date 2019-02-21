package net.arksea.dsf.service;

/**
 *  HightThreshol > LowThreshold > 系统平均响应时间
 *  此三个值应当设置足够的间隔，以避免系统产生震荡（限流值上下频繁调整）
 *  上门限 用于开启/提升限流, 建议为系统平稳运行时的平均响应时间的2~4倍，
 *        应小于系统异常时平均响应时间，小于设置的请求超时时间
 *  下门限 用于关闭/降低限流, 建议为系统平稳运行时的平均响应时间的1.5~2倍，
 *        必须小于上门限， 大于系统正常运行时平均响应时间
 * Created by xiaohaixing on 2019/1/23.
 */
public interface IRateLimitConfig {
    long getHightThreshold();//平均响应时间上门限
    long getLowThreshold();  //平均响应时间下门限
    default int  getMinLimitQps()   { return 10; }//最小限流流量， 保持少量的请求用于尝试和统计，有助于正确判断系统是否恢复正常
    default float getUpdateRatio()  { return 1.5f; } //调整限流流量的系数， 此系数设置太大容易引起系统震荡，设置太小限流调整过慢
    default long getUpdatePeriodMinutes() { return 2; } //调整限流值的最小间隔，以分钟为单位
    Object getRateLimitResponse(); //当被限流时服务的返回值，例如 {"code": 1, "message": "rate limit"}
}
