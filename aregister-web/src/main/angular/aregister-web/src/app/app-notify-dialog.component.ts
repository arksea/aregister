import { Component, Injectable } from '@angular/core';
import { AppNotifyDialogService } from './app-notify-dialog.service';
import { NotifyEvent } from './app-notify-dialog.service';

@Component({
  selector: 'app-notify-dialog',
  templateUrl: './app-notify-dialog.component.html',
})
export class AppNotifyDialogComponent {

  public opened = false;
  public notifyEvent: NotifyEvent;

  constructor(private service: AppNotifyDialogService) {
      this.notifyEvent = {
        title: '',
        message: '',
        description: '',
        confirm: false,
        selection: null
      };
      service.notify.subscribe(
          e => {
              this.open(e);
          }
      );
  }

  public open(e: NotifyEvent): void {
    this.notifyEvent = e;
    this.opened = true;
  }

  public close(selection: boolean): void {
    this.opened = false;
    if (this.notifyEvent.selection) {
      this.notifyEvent.selection.next(selection);
      this.notifyEvent.selection.complete();
    }
  }
}
