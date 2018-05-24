import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs';
import { RestResult,ServiceList } from '../models'

@Injectable()
export class ServiceAPI {
    headers: HttpHeaders;

    public constructor(private http: HttpClient) {
        this.headers = new HttpHeaders();
        this.headers.append('Content-Type', 'application/json; charset=UTF-8');
    }

  public getServiceList(): Observable<RestResult<ServiceList>> {
    return this.http.get(environment.apiUrl + '/api/v1/services') as Observable<RestResult<ServiceList>>;
//    return new BehaviorSubject<RestResult<ServiceList>>({
//      code: 0,
//      result: { items:['ServiceA', 'ServiceB']},
//      reqid: '1234'
//    });
  }
}
