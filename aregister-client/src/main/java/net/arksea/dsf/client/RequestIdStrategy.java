package net.arksea.dsf.client;

/**
 * 唯一请求ID策略
 * Create by xiaohaixing on 2020/9/21
 */
public enum RequestIdStrategy {
    ORIGINAL, //直接使用调用者传入的原始值,这种策略很危险，如果客户端传入的ID不唯一，就会造成请求消息丢、串等严重后果，所以不能确认ID为唯一就不要使用此方式
    POSTFIX_LESS_32,//长度小于32则加后缀，否则使用原始值
    POSTFIX,  //在原始值后添加后缀
    REGENERATE,//重新生成，默认值
    REGENERATE_LESS_32 //长度小于32则重新生成，否则使用原始值
}