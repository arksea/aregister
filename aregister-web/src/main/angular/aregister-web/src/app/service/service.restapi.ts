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
import * as ServiceActions from './service.actions';
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
            tap((r: RestResult<ServiceList>) => this.handleErrorResult(r, method, '', this.store)),
            catchError(r => this.handleCatchedError(r, method, '', this.store))
        );
    }

    public getServiceTree(): Observable<RestResult<ServiceNamespace[]>> {
        //先调用tab，后调用catchError，是为了防止tab继续处理catchError的返回值
        let method = 'Request service tree';
        return this.http.get(environment.apiUrl + '/api/v1/services/tree').pipe(
            tap((r: RestResult<ServiceNamespace[]>) => this.handleErrorResult(r, method, '', this.store)),
            catchError(r => this.handleCatchedError(r, method, '', this.store))
        );
    }

    public getService(name: string): Observable<RestResult<Service>> {
        //先调用tab，后调用catchError，是为了防止tab继续处理catchError的返回值
        let method = 'Request service runtime';
        return this.http.get(environment.apiUrl + '/api/v1/services/'+name+'/runtime').pipe(
            tap((r: RestResult<Service>) => this.handleErrorResult(r, method, name, this.store)),
            catchError(r => this.handleCatchedError(r, method, name, this.store))
        );
    }

    public getRequestCountHistory(servicePath: string): Observable<RestResult<RequestCountHistory>> {
        let method = 'Request service request count history';
        return this.http.get(environment.apiUrl + '/api/v1/services/request?path=' + encodeURIComponent(servicePath))
            .pipe(
                tap((r: RestResult<RequestCountHistory>) => this.handleErrorResult(r, method, name, this.store)),
                catchError(r => this.handleCatchedError(r, method, name, this.store)
            )
        );
    }

    public onUpdateService(regname: string) {
            const state: AppState = this.store.getState() as AppState;
            this.getService(regname).subscribe(
                (r: RestResult<Service>) => {
                    if (r.code == 0) {
                        let act = ServiceActions.updateService(r.result);
                        this.store.dispatch(act);
                        for (let i = 0; i< r.result.instances.length; i++) {
                            let inst : Instance = r.result.instances[i];
                            if (inst.online) {
                                this.getRequestCountHistory(inst.path).subscribe(
                                    (h: RestResult<RequestCountHistory>) => {
                                        let actC = ServiceActions.updateRequestCount(regname,i,h.result);
                                        this.store.dispatch(actC);
                                    }
                                );
                            }
                        };
                    }
                }
            );
    }

    private handleCatchedError(error, method: string, args: string, store: Store<AppState>) {
        let act = SystemEventActions.newEvent(method+' failed: '+ error.message + ', args=' + args);
        store.dispatch(act);
        return new BehaviorSubject(error);
    };

    private handleErrorResult(error, method: string, args: string, store: Store<AppState>) {
        let msg = error.code == 0 ? 'succeed' : 'failed' + error.error;
        let act = SystemEventActions.newEvent(method+' '+msg+' : '+args);
        store.dispatch(act);
        return new BehaviorSubject(error);
    }
}

