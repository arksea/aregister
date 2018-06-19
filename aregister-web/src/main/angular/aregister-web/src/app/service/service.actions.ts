import { Action, ActionCreator } from 'redux';
import { Service, ServiceNamespace,ServiceVersion,RequestCountHistory } from '../models';

export const UPDATE_SERVICE_TREE = '[ServiceTree] Update tree';
export interface UpdateServiceTreeAction extends Action {
  serviceTree: ServiceNamespace[];
}
export const updateServiceTree: ActionCreator<UpdateServiceTreeAction> =
  (tree) => ({
    type: UPDATE_SERVICE_TREE,
    serviceTree: tree
  });

export const UPDATE_SERVICE = '[Service] Update service';
export interface UpdateServiceAction extends Action {
  service: Service;
}
export const updateService: ActionCreator<UpdateServiceAction> =
  (svc) => ({
    type: UPDATE_SERVICE,
    service: svc
  });

export const UPDATE_REQUEST_COUNT = '[Service] Update request count';
export interface UpdateRequestCountAction extends Action {
  name: string;
  index: number;
  history: RequestCountHistory;
}
export const updateRequestCount: ActionCreator<UpdateRequestCountAction> =
  (n,i,his) => ({
    type: UPDATE_REQUEST_COUNT,
    name: n,
    index: i,
    history: his
  });
