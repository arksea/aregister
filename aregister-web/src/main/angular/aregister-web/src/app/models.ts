export interface RestResult<T> {
  code: number;
  result: T;
  reqid: string;
};

export interface ServiceList {
  items: string[];
};
