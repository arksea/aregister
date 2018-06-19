export interface RestResult<T> {
  code: number;
  result?: T;
  error?: string;
  reqid: string;
};

export interface ServiceList {
  items: string[];
};

export interface Service {
  name: string;
  instances: Instance[];
  subscribers: Subscriber[];
};

export interface Instance {
  addr:            string;
  online:          boolean;
  path?:           string;
  unregistered?:   boolean;
  registerTime?:   number;
  unregisterTime?: number;
  lastOfflineTime?:number;
  lastOnlineTime?: number;
  qps?: number;
  tts?: number;
};

export interface Subscriber {
  name: string;
  count: number;
};

export interface ServiceVersion {
  version: string;
  regname: string;
};

export interface ServiceSeries {
  name: string;
  expanded: boolean;
  versions: ServiceVersion[];
};

export interface ServiceNamespace {
  namespace: string;
  expanded: boolean;
  serviceList: ServiceSeries[];
};

export interface RequestCount {
  requestCount: number;
  succeedCount: number;
  respondTime: number;
};

//服务实例请求计数历史数据，每分钟一条
export interface RequestCountHistory {
  items: RequestCount[];
}
