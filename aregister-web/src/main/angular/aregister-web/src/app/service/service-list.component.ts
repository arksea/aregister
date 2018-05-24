import { Component, Inject } from '@angular/core';
import { Store } from 'redux';
import { AppStore } from '../app-store';
import { AppState } from '../app-state';
import { ServiceAPI } from './service.restapi';
import * as ServiceListActions from './service-list.actions';

@Component({
  selector: 'service-list',
  templateUrl: './service-list.component.html'
})
export class ServiceListComponent {
  serviceList: string[];

  constructor(@Inject(AppStore) private store: Store<AppState>,
              private api: ServiceAPI) {
    store.subscribe(() => this.update());
    api.getServiceList()
       .subscribe(list => store.dispatch(ServiceListActions.updateServiceList(list)));
    this.update();
  }

  update() {
    const state: AppState = this.store.getState() as AppState;
    this.serviceList = state.serviceList.items;
  }
}
