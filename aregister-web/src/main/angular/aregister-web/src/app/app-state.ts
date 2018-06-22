import { combineReducers, Reducer } from 'redux';
import { SystemEventState, SystemEventReducer } from './system/system-event.reducer';


export interface AppState {
    readonly systemEvent: SystemEventState
}

export const rootReducer: Reducer<AppState> = combineReducers<AppState>({
    systemEvent: SystemEventReducer
});
