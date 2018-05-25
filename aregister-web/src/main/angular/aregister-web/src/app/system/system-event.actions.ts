import { Action, ActionCreator } from 'redux';

export const NEW_EVENT = '[System] New event';
export interface NewEventAction extends Action {
  message: string;
}
export const newEvent: ActionCreator<NewEventAction> =
  (message) => ({
    type: NEW_EVENT,
    message: message
  });
