import { Component, Inject, OnInit, Input } from '@angular/core';
import { Router, ActivatedRoute, ParamMap,NavigationEnd  } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Store } from 'redux';
import { AppStore } from '../app-store';
import { AppState } from '../app-state';
import { ServiceAPI } from './service.restapi';
import * as ServiceActions from './service.actions';
import * as SystemEventActions from '../system/system-event.actions';
import { RestResult,Service,Instance } from '../models'

@Component({
  selector: 'service',
  templateUrl: './service.component.html'
})
export class ServiceComponent {
    service: Service = {
        name: '',
        instances: [],
        subscribers: []
    } as Service;

    constructor(@Inject(AppStore) private store: Store<AppState>,
                private api: ServiceAPI) {
        store.subscribe(() => this.refresh());
    }

    ngOnInit(): void {
    }

    refresh() {
        const state: AppState = this.store.getState() as AppState;
        if (state.services.selectedVersion) {
            let svc = state.services.serviceMap[state.services.selectedVersion.regname];
            if (svc) {
                this.service = svc;
            }
        }
    }

    //格式化时间展示
    formatTime(time: number): string {
         if (time > 0) {
           let date: Date = new Date();
           date.setTime(time);
           return date.toLocaleString();
         } else {
           return '';
         }
    }
}
