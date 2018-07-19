import { Component, Inject } from '@angular/core';
import { SystemDAO } from './system.dao';

@Component({
  selector: 'system-status-bar',
  templateUrl: './status-bar.component.html'
})
export class SystemStatusBarComponent {
    eventMessage = this.systemDao.currentEventMessage;

    constructor(private systemDao: SystemDAO) {

    }
}
