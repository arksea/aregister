import { Action, ActionCreator } from 'redux';
import { Service, ServiceNamespace } from '../models';

export const UPDATE_SERVICE_TREE = '[Service] Update tree';
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
