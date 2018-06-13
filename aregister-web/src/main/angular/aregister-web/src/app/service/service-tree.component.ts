import { Component, Inject, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Store } from 'redux';
import { AppStore } from '../app-store';
import { AppState } from '../app-state';
import { ServiceAPI } from './service.restapi';
import * as ServiceActions from './service.actions';
import * as SystemEventActions from '../system/system-event.actions';
import { RestResult,ServiceNamespace,Service } from '../models'

@Component({
  selector: 'service-tree',
  templateUrl: './service-tree.component.html'
})
export class ServiceTreeComponent {
    serviceTree =  [];

    constructor(@Inject(AppStore) private store: Store<AppState>, private api: ServiceAPI) {
        store.subscribe(() => this.refresh());
    }

    ngOnInit(): void {
        this.onClickRefreshBtn();
    }

    refresh() {
        const state: AppState = this.store.getState() as AppState;
        this.serviceTree = state.services.serviceTree;
    }

    onClickRefreshBtn() {
        this.api.getServiceTree().subscribe(
            (r: RestResult<ServiceNamespace[]>) => {
                if (r.code == 0) {
                    let act = ServiceActions.updateServiceTree(r.result);
                    this.store.dispatch(act);
                }
            }
        );
    }

//    onClickOneService(name: string) {
//        const state: AppState = this.store.getState() as AppState;
//            this.api.getService(name).subscribe(
//                (r: RestResult<Service>) => {
//                    if (r.code == 0) {
//                        let act = ServiceActions.updateService(r.result);
//                        this.store.dispatch(act);
//                    }
//                }
//            );
//    }
}

