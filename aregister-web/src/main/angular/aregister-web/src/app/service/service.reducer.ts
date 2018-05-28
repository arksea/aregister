import { Action } from 'redux';
import * as Actions from './service.actions';
import { Service } from '../models';

export interface ServicesState {
  readonly serviceList: string[];
  readonly currentService: string;
  readonly serviceMap: Map<string, Service>;
}

const initialState: ServicesState = {
  serviceList: [],
  currentService: 'net.arksea.dsf.DemoService-1.0',
  serviceMap: new Map<string, Service>()
};

export const ServicesReducer = function(state: ServicesState = initialState, action: Action): ServicesState {
    switch (action.type) {
        case Actions.UPDATE_SERVICE_LIST:
            const list: string[] = (<Actions.UpdateServiceListAction>action).serviceList;
            return Object.assign({}, state, {
                serviceList : list
            });
        case Actions.UPDATE_SERVICE:
            const service: Service =  (<Actions.UpdateServiceAction>action).service;
            const map: Map<string,Service> = Object.assign({}, state.serviceMap, {});
            map[service.name] = service;
            return Object.assign({}, state, {
                currentService: service.name,
                serviceMap: map
            });
        default:
            return state;
    }
};

