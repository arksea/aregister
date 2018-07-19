import { Injectable,Inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs';
import { map, catchError,tap } from 'rxjs/operators';
import { SystemDAO } from '../system/system.dao';
import { RestResult,ServiceList,Service, ServiceVersion, ServiceSeries, ServiceNamespace,
         RequestCountHistory,Instance
       } from '../models'

@Injectable()
export class ServiceAPI {
    headers: HttpHeaders;

    public constructor(private systemDao: SystemDAO, private http: HttpClient) {
        this.headers = new HttpHeaders();
        this.headers.append('Content-Type', 'application/json; charset=UTF-8');
    }

    public getServiceTree(): Observable<RestResult<ServiceNamespace[]>> {
        let method = 'Request service tree';
        return this.http.get(environment.apiUrl + '/api/v1/services/tree').pipe(
            tap((r: RestResult<ServiceNamespace[]>) => this.handleResult(r, method, '')),
            catchError(r => this.handleCatchedError(r, method, ''))
        );
    }

    public getService(name: string): Observable<RestResult<Service>> {
        let method = 'Request service runtime';
        return this.http.get(environment.apiUrl + '/api/v1/services/'+name+'/runtime').pipe(
            tap((r: RestResult<Service>) => this.handleResult(r, method, name)),
            catchError(r => this.handleCatchedError(r, method, name))
        );
    }

    public getRequestCountHistory(instAddr: string, servicePath: string): Observable<RestResult<RequestCountHistory>> {
        let method = 'Request service request count history';
        return this.http.get(environment.apiUrl + '/api/v1/services/request?path=' + encodeURIComponent(servicePath))
            .pipe(
                tap((r: RestResult<RequestCountHistory>) => this.handleResult(r, method, instAddr)),
                catchError(r => this.handleCatchedError(r, method, instAddr)
            )
        );
    }

    private handleCatchedError(error, method: string, args: string) {
        this.systemDao.newEvent(method+' failed: '+ error.message + ', args=' + args);
        return new BehaviorSubject(error);
    };

    private handleResult(result, method: string, args: string) {
        let msg = result.code == 0 ? 'succeed' : 'failed' + result.error;
        this.systemDao.newEvent(method+' '+msg+' : '+args);
        return new BehaviorSubject(result);
    }
}
