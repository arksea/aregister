import { Action, ActionCreator } from 'redux';

export const UPDATE_SERVICE_LIST = '[Service] Update list';
export interface UpdateServiceListAction extends Action {
  serviceList: string[];
}
export const updateServiceList: ActionCreator<UpdateServiceListAction> =
  (list) => ({
    type: UPDATE_SERVICE_LIST,
    serviceList: list
  });
