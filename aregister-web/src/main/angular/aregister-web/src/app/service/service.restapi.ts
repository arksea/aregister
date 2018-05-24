import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs';

@Injectable()
export class ServiceAPI {
    headers: HttpHeaders;

    public constructor(private http: HttpClient) {
        this.headers = new HttpHeaders();
        this.headers.append('Content-Type', 'application/json; charset=UTF-8');
    }

// return this.http.get(environment.apiUrl + '/api/v1/projects') as Observable<ServiceList>;
  public getServiceList(): Observable<string[]> {
    return new BehaviorSubject<string[]>(['ServiceA', 'ServiceB']);
  }
}
