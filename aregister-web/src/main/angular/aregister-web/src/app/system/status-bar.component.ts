import { Component, Inject } from '@angular/core';
import { Store } from 'redux';
import { AppStore } from '../app-store';
import { AppState } from '../app-state';
import * as ServiceListActions from './system-event.actions';

@Component({
  selector: 'system-status-bar',
  templateUrl: './status-bar.component.html'
})
export class SystemStatusBarComponent {
  eventMessage: string = 'hello world!';

  constructor(@Inject(AppStore) private store: Store<AppState>) {
    store.subscribe(() => this.refresh());
  }

  refresh() {
    const state: AppState = this.store.getState() as AppState;
    this.eventMessage = state.systemEvent.current;
  }
}
