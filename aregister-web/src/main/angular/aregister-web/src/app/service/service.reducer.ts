import { Action } from 'redux';
import * as Actions from './service.actions';
import { Service,ServiceNamespace,ServiceVersion } from '../models';

export interface ServicesState {
  readonly serviceTree: ServiceNamespace[];
  readonly serviceMap: Map<string, Service>;
}

const initialState: ServicesState = {
  serviceTree: [],
  serviceMap: new Map<string, Service>()
};

export const ServicesReducer = function(state: ServicesState = initialState, action: Action): ServicesState {
    switch (action.type) {
        case Actions.UPDATE_SERVICE_TREE:
            const tree: ServiceNamespace[] = (<Actions.UpdateServiceTreeAction>action).serviceTree;
            return Object.assign({}, state, {
                serviceTree : tree
            });
        case Actions.UPDATE_SERVICE:
            const service: Service =  (<Actions.UpdateServiceAction>action).service;
            const map: Map<string,Service> = Object.assign({}, state.serviceMap, {});
            map[service.name] = service;
            return Object.assign({}, state, {
                serviceMap: map
            });
        default:
            return state;
    }
};

