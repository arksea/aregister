import { Action } from 'redux';
import * as Actions from './service.actions';
import { Service,ServiceNamespace,ServiceVersion,RequestCountHistory,RequestCount,Instance } from '../models';

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
        case Actions.UPDATE_REQUEST_COUNT:
            const act: Actions.UpdateRequestCountAction = <Actions.UpdateRequestCountAction>action;
            if (act.history.items.length > 2) {
                let c1: RequestCount = act.history.items[1];
                let c2: RequestCount = act.history.items[2];
                let svc: Service = state.serviceMap[act.name];
                let inst: Instance = svc.instances[act.index];
                let count = c1.requestCount - c2.requestCount;
                inst.qps = Math.round(count/60);
                inst.tts = Math.round((c1.respondTime - c2.respondTime) / count);
                let rate = (c1.succeedCount - c2.succeedCount) / count;
                inst.succeedRate = Math.round(rate*1000) / 10;
                return state;
            } else {
                return state;
            }
        default:
            return state;
    }
};

