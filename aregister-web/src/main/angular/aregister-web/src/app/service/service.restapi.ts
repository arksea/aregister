import { Injectable,Inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs';
import { map, catchError,tap } from 'rxjs/operators';
import { RestResult,ServiceList,Service, ServiceVersion, ServiceSeries, ServiceNamespace,
         RequestCountHistory,Instance
       } from '../models'

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
        let method = 'Request service list';
        return this.http.get(environment.apiUrl + '/api/v1/services/list').pipe(
            tap((r: RestResult<ServiceList>) => this.handleResult(r, method, '', this.store)),
            catchError(r => this.handleCatchedError(r, method, '', this.store))
        );
    }

    public getServiceTree(): Observable<RestResult<ServiceNamespace[]>> {
        let method = 'Request service tree';
        return this.http.get(environment.apiUrl + '/api/v1/services/tree').pipe(
            tap((r: RestResult<ServiceNamespace[]>) => this.handleResult(r, method, '', this.store)),
            catchError(r => this.handleCatchedError(r, method, '', this.store))
        );
    }

    public getService(name: string): Observable<RestResult<Service>> {
        let method = 'Request service runtime';
        return this.http.get(environment.apiUrl + '/api/v1/services/'+name+'/runtime').pipe(
            tap((r: RestResult<Service>) => this.handleResult(r, method, name, this.store)),
            catchError(r => this.handleCatchedError(r, method, name, this.store))
        );
    }

    public getRequestCountHistory(servicePath: string): Observable<RestResult<RequestCountHistory>> {
        let method = 'Request service request count history';
        return this.http.get(environment.apiUrl + '/api/v1/services/request?path=' + encodeURIComponent(servicePath))
            .pipe(
                tap((r: RestResult<RequestCountHistory>) => this.handleResult(r, method, name, this.store)),
                catchError(r => this.handleCatchedError(r, method, name, this.store)
            )
        );
    }

    private handleCatchedError(error, method: string, args: string, store: Store<AppState>) {
        let act = SystemEventActions.newEvent(method+' failed: '+ error.message + ', args=' + args);
        store.dispatch(act);
        return new BehaviorSubject(error);
    };

    private handleResult(result, method: string, args: string, store: Store<AppState>) {
        let msg = result.code == 0 ? 'succeed' : 'failed' + result.error;
        let act = SystemEventActions.newEvent(method+' '+msg+' : '+args);
        store.dispatch(act);
        return new BehaviorSubject(result);
    }
}

