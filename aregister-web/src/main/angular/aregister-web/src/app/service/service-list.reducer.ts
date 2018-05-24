import { Action } from 'redux';
import * as Actions from './service-list.actions';

export interface ServiceListState {
  items: string[];
}

const initialState: ServiceListState = {
  items: []
};

export const ServiceListReducer = function(state: ServiceListState = initialState, action: Action): ServiceListState {
  switch (action.type) {
    case Actions.UPDATE_SERVICE_LIST:
      const list: string[] = (<Actions.UpdateServiceListAction>action).serviceList;
      return {
        items : list
      };
    default:
      return state;
  }
};
