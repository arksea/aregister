import { Component, Inject, OnInit, Input } from '@angular/core';
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
    service: Service;

    constructor(@Inject(AppStore) private store: Store<AppState>, private api: ServiceAPI) {
        store.subscribe(() => this.refresh());
    }

    ngOnInit(): void {
        this.onClickRefreshBtn();
    }

    refresh() {
        const state: AppState = this.store.getState() as AppState;
        this.service = state.services.serviceMap[state.services.currentService];
    }

    onClickRefreshBtn() {
        const state: AppState = this.store.getState() as AppState;
        if (state.services.currentService) {
            this.api.getService(state.services.currentService).subscribe(
                (r: RestResult<Service>) => {
                    if (r.code == 0) {
                        let act = ServiceActions.updateService(r.result);
                        this.store.dispatch(act);
                    }
                }
            );
        }
    }
}
