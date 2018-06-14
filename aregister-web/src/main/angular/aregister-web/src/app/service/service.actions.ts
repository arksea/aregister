import { Action, ActionCreator } from 'redux';
import { Service, ServiceNamespace,ServiceVersion } from '../models';

export const UPDATE_SERVICE_TREE = '[ServiceTree] Update tree';
export interface UpdateServiceTreeAction extends Action {
  serviceTree: ServiceNamespace[];
}
export const updateServiceTree: ActionCreator<UpdateServiceTreeAction> =
  (tree) => ({
    type: UPDATE_SERVICE_TREE,
    serviceTree: tree
  });

export const SELECT_SERVICE_TREE_NODE = '[ServiceTree] Select service';
export interface SelectServiceTreeNodeAction extends Action {
  serviceVersion: ServiceVersion;
}
export const selectServiceTreeNodeAction: ActionCreator<SelectServiceTreeNodeAction> =
  (svc) => ({
    type: SELECT_SERVICE_TREE_NODE,
    serviceVersion: svc
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
