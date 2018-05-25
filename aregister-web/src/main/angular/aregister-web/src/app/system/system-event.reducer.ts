import { Action } from 'redux';
import * as Actions from './system-event.actions';

export interface SystemEventState {
  current: string;
}

const initialState: SystemEventState = {
  current: ""
};

export const SystemEventReducer = function(state: SystemEventState = initialState, action: Action): SystemEventState {
  switch (action.type) {
    case Actions.NEW_EVENT:
      const message: string = (<Actions.NewEventAction>action).message;
      return Object.assign({}, state, {
        current: message
      });
    default:
      return state;
  }
};

