import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';

export interface NotifyEvent {
    title: string;
    message: string;
    description: any;
    confirm: boolean;
    selection: Subject<boolean>;
}

@Injectable()
export class AppNotifyDialogService {

    public notify: Subject<NotifyEvent> = new Subject<NotifyEvent>();
    public open(message: string): void {
        const event: NotifyEvent = {
            title: 'Notify',
            message: message,
            description: null,
            confirm: false,
            selection: null
        };
        this.notify.next(event);
    }

    public openWidthTitle(title: string, message: string): void {
        const event: NotifyEvent = {
            title: title,
            message: message,
            description: null,
            confirm: false,
            selection: null
        };
        this.notify.next(event);
    }

    public openWidthDescription(title: string, message: string, description: string): void {
        const event: NotifyEvent = {
            title: title,
            message: message,
            description: description,
            confirm: false,
            selection: null
        };
        this.notify.next(event);
    }

    public openWidthConfirm(title: string, message: string, description: string): Subject<boolean> {
        const selection = new Subject<boolean>();
        const event: NotifyEvent = {
            title: title,
            message: message,
            description: description,
            confirm: true,
            selection: selection
        };
        this.notify.next(event);
        return selection;
    }
}
