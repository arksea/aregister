import { Action, ActionCreator } from 'redux';
import { Service } from '../models';

export const UPDATE_SERVICE_LIST = '[Service] Update list';
export interface UpdateServiceListAction extends Action {
  serviceList: string[];
}
export const updateServiceList: ActionCreator<UpdateServiceListAction> =
  (list) => ({
    type: UPDATE_SERVICE_LIST,
    serviceList: list
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
