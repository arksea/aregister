import { Action } from 'redux';
import * as Actions from './service.actions';
import { Service,ServiceNamespace,ServiceVersion } from '../models';

export interface ServicesState {
  readonly serviceTree: ServiceNamespace[];
  readonly selectedVersion: ServiceVersion;
  readonly serviceMap: Map<string, Service>;
}

const initialState: ServicesState = {
  serviceTree: [],
  selectedVersion: null,
  serviceMap: new Map<string, Service>()
};

export const ServicesReducer = function(state: ServicesState = initialState, action: Action): ServicesState {
    switch (action.type) {
        case Actions.UPDATE_SERVICE_TREE:
            const tree: ServiceNamespace[] = (<Actions.UpdateServiceTreeAction>action).serviceTree;
            return Object.assign({}, state, {
                serviceTree : tree
            });
        case Actions.SELECT_SERVICE_TREE_NODE:
            const svc: ServiceVersion = (<Actions.SelectServiceTreeNodeAction>action).serviceVersion;
            if (state.selectedVersion != null) {
                state.selectedVersion.active = false;
            }
            svc.active = true;
            return Object.assign({}, state, {
                selectedVersion: svc
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

