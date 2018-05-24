import { ServiceListState, ServiceListReducer } from './service/service-list.reducer';
import { combineReducers, Reducer } from 'redux';

export interface AppState {
    serviceList: ServiceListState;
}

export const rootReducer: Reducer<AppState> = combineReducers<AppState>({
    serviceList: ServiceListReducer
});
