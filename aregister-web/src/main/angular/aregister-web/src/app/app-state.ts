import { combineReducers, Reducer } from 'redux';
import { ServicesState, ServicesReducer } from './service/service.reducer';
import { SystemEventState, SystemEventReducer } from './system/system-event.reducer';


export interface AppState {
    services: ServicesState,
    systemEvent: SystemEventState
}

export const rootReducer: Reducer<AppState> = combineReducers<AppState>({
    services: ServicesReducer,
    systemEvent: SystemEventReducer
});
