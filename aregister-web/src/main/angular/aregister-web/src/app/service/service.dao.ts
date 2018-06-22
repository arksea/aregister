import { Injectable } from '@angular/core';
import { Subject, BehaviorSubject, Observable } from 'rxjs';
import { ServiceNamespace,Service,RestResult,Instance,Quality,
         Subscriber,RequestCountHistory,RequestCount } from '../models';
import { ServiceAPI } from './service.restapi';


@Injectable()
export class ServiceDAO {
    serviceTree: Subject<ServiceNamespace[]> = new BehaviorSubject<ServiceNamespace[]>([]);
    selectedService: Subject<string> = new BehaviorSubject<string>(null);
    currentService: Subject<Service> = new BehaviorSubject<Service>(null);
    instances: Subject<Instance[]> = new BehaviorSubject<Instance[]>([]);
    subscribers: Subject<Subscriber[]> = new BehaviorSubject<Subscriber[]>([]);

    constructor(private api: ServiceAPI) {
        this.selectedService.subscribe(regname => {
            if (regname != null) {
                this.api.getService(regname).subscribe(r => {
                    if (r.code == 0) {
                        let service: Service = r.result;
                        for (let i = 0; i< service.instances.length; i++) {
                            let inst : Instance = service.instances[i];
                            inst.quality = new BehaviorSubject<Quality>({qps:0,tts:0,succeedRate:100});
                            if (inst.online) {
                                this.updateRquestCount(inst);
                            }
                        }
                        this.instances.next(service.instances);
                        this.subscribers.next(service.subscribers);
                    }
                });
            }
        });
    }

    public updateRquestCount(inst: Instance): void {
        this.api.getRequestCountHistory(inst.addr, inst.path).subscribe (
            (h: RestResult<RequestCountHistory>) => {
                if (h.code == 0) {
                    inst.quality.next(this.countQuality(h.result));
                }
            }
        )
    }

    private countQuality(history: RequestCountHistory): Quality {
        if (history.items.length > 2) {
            let c1: RequestCount = history.items[1];
            let c2: RequestCount = history.items[2];
            let count = c1.requestCount - c2.requestCount;
            let qps = count/60;
            if (qps < 5) {
                qps = Math.round(qps*10)/10;
            } else {
                qps = Math.round(qps);
            }
            let tts = 0;
            let rate = 1;
            if (count>0) {
                tts = (c1.respondTime - c2.respondTime) / count;
                rate = (c1.succeedCount - c2.succeedCount) / count;
            }
            tts = Math.round(tts);
            rate = rate*1000 / 10;
            return {qps:qps,tts:tts,succeedRate:rate};
        } else {
            return {qps:0, tts:0, succeedRate: 100};
        }
    }

    public updateServiceTree(): void {
        this.api.getServiceTree().subscribe(
            (r: RestResult<ServiceNamespace[]>) => {
                if (r.code == 0) {
                    this.serviceTree.next(r.result);
                }
            }
        );
    }

    public selectService(regname: string): void {
        this.selectedService.next(regname);
    }
}
