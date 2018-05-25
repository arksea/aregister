import { Injectable,Inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs';
import { map, catchError,tap } from 'rxjs/operators';
import { RestResult,ServiceList } from '../models'

import { Store } from 'redux';
import { AppStore } from '../app-store';
import { AppState } from '../app-state';
import * as SystemEventActions from '../system/system-event.actions';

@Injectable()
export class ServiceAPI {
    headers: HttpHeaders;

    public constructor(@Inject(AppStore) private store: Store<AppState>, private http: HttpClient) {
        this.headers = new HttpHeaders();
        this.headers.append('Content-Type', 'application/json; charset=UTF-8');
    }

    public getServiceList(): Observable<RestResult> {
        //先调用tab，后调用catchError，是为了防止tab继续处理catchError的返回值
        return this.http.get(environment.apiUrl + '/api/v1/services').pipe(
            tap((r: RestResult) => this.handleErrorResult(r, this.store)),
            catchError(r => this.handleCatchedError(r, this.store))
        );
    }

    private handleCatchedError(error, store: Store<AppState>) {
        let act = SystemEventActions.newEvent('Request service list failed: '+error.message);
        store.dispatch(act);
        return new BehaviorSubject(error);
    };

    private handleErrorResult(error: RestResult, store: Store<AppState>) {
        if (error.code != 0) {
            let act = SystemEventActions.newEvent('Request service list failed: '+error.result);
            store.dispatch(act);
        }
        return new BehaviorSubject(error);
    }
}

