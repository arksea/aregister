package net.arksea.dsf.client;

/**
 * 唯一请求ID策略
 * Create by xiaohaixing on 2020/9/21
 */
public enum RequestIdStrategy {
    ORIGINAL, //直接使用调用者传入的原始值
    POSTFIX_LESS_32,//长度小于32则加后缀，否则使用原始值
    POSTFIX,  //在原始值后添加后缀
    REGENERATE//重新生成，默认值
}