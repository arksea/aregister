import { Injectable,Inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs';
import { map, catchError,tap } from 'rxjs/operators';
import { RestResult,ServiceList,Service, ServiceVersion, ServiceSeries, ServiceNamespace } from '../models'

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

    public getServiceList(): Observable<RestResult<ServiceList>> {
        //先调用tab，后调用catchError，是为了防止tab继续处理catchError的返回值
        let method = 'Request service list';
        return this.http.get(environment.apiUrl + '/api/v1/services/list').pipe(
            tap((r: RestResult<ServiceList>) => this.handleErrorResult(r, method, this.store)),
            catchError(r => this.handleCatchedError(r, method, this.store))
        );
    }

    public getServiceTree(): Observable<RestResult<ServiceNamespace[]>> {
        //先调用tab，后调用catchError，是为了防止tab继续处理catchError的返回值
        let method = 'Request service tree';
        return this.http.get(environment.apiUrl + '/api/v1/services/tree').pipe(
            tap((r: RestResult<ServiceNamespace[]>) => this.handleErrorResult(r, method, this.store)),
            catchError(r => this.handleCatchedError(r, method, this.store))
        );
    }

    public getService(name: string): Observable<RestResult<Service>> {
        //先调用tab，后调用catchError，是为了防止tab继续处理catchError的返回值
        let method = 'Request service runtime';
        return this.http.get(environment.apiUrl + '/api/v1/services/'+name+'/runtime').pipe(
            tap((r: RestResult<Service>) => this.handleErrorResult(r, method, this.store)),
            catchError(r => this.handleCatchedError(r, method, this.store))
        );
    }

    private handleCatchedError(error, method: string, store: Store<AppState>) {
        let act = SystemEventActions.newEvent(method+' failed: '+error.message);
        store.dispatch(act);
        return new BehaviorSubject(error);
    };

    private handleErrorResult(error, method: string, store: Store<AppState>) {
        let msg = error.code == 0 ? 'succeed' : 'failed' + error.error;
        let act = SystemEventActions.newEvent(method+' '+msg);
        store.dispatch(act);
        return new BehaviorSubject(error);
    }
}

