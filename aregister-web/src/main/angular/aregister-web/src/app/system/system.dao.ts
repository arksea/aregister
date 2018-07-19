import { Injectable } from '@angular/core';
import { Subject, BehaviorSubject, Observable } from 'rxjs';
import { ServiceNamespace } from '../models';


@Injectable()
export class SystemDAO {
    currentEventMessage: Subject<string> = new BehaviorSubject<string>("");
    constructor() {
    }

    public newEvent(msg: string) {
        this.currentEventMessage.next(msg);
    }
}
