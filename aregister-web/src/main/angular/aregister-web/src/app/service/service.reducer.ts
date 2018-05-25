import { Action } from 'redux';
import * as Actions from './service.actions';

export interface ServicesState {
  readonly items: string[];
}

const initialState: ServicesState = {
  items: []
};

export const ServicesReducer = function(state: ServicesState = initialState, action: Action): ServicesState {
  switch (action.type) {
    case Actions.UPDATE_SERVICE_LIST:
      const list: string[] = (<Actions.UpdateServiceListAction>action).serviceList;
      return Object.assign({}, state, {
        items : list
      });
    default:
      return state;
  }
};

