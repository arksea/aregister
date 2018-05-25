export interface RestResult<T> {
  code: number;
  result?: T;
  error?: string;
  reqid: string;
};

export interface ServiceList {
  items: string[];
};
