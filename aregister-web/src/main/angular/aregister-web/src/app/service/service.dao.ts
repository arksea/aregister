import { Injectable } from '@angular/core';
import { Subject, BehaviorSubject, Observable } from 'rxjs';
import { ServiceNamespace,Service,RestResult,Instance,Quality,
         Subscriber,RequestCountHistory,RequestCount } from '../models';
import { ServiceAPI } from './service.restapi';
import { scan, map, publishReplay, refCount } from 'rxjs/operators';

type IInstancesOperation = (instances: Instance[]) => Instance[];
interface UpdateReg {
    addr: string;
    unregistered: boolean;
}

@Injectable()
export class ServiceDAO {
    // updateReg       ───┬───▶ instanceUpdates ─────▶ instance
    // updateInstances ───┘
    //                   ┌──▶ subscribers
    // selectedService ──┴──▶ updateInstances

    serviceTree: Subject<ServiceNamespace[]> = new BehaviorSubject<ServiceNamespace[]>([]);
    selectedService: Subject<string> = new BehaviorSubject<string>(null);

    subscribers: Subject<Subscriber[]> = new BehaviorSubject<Subscriber[]>([]);

    instances: Observable<Instance[]>;
    instanceUpdates: Subject<any> = new Subject<any>();
    updateReg: Subject<UpdateReg> = new Subject();
    updateInstances: Subject<Instance[]> = new Subject();

    constructor(private api: ServiceAPI) {
        this.selectedService.subscribe(regname => {
            if (regname != null) {
                this.api.getService(regname).subscribe(r => {
                    if (r.code == 0) {
                        let service: Service = r.result;
                        this.updateInstances.next(service.instances);
                        this.subscribers.next(service.subscribers);
                        for (let i = 0; i< service.instances.length; i++) {
                            let inst : Instance = service.instances[i];
                            inst.serviceName = regname;
                            inst.quality = new BehaviorSubject<Quality>({count:0,qpm:0,tts:0,succeedRate:100});
                            if (inst.online) {
                                this.updateRquestCount(inst);
                            }
                        }
                    }
                });
            }
        });

        this.updateInstances.pipe(
            map(function(instances: Instance[]): IInstancesOperation {
                return (old: Instance[]) => {
                    return instances;
                };
            })
        ).subscribe(this.instanceUpdates);
        this.updateReg.pipe(
            map(function(msg: UpdateReg): IInstancesOperation {
                return (old: Instance[]) => {
                    let instances: Instance[] = [];
                    old.forEach(it => {
                        if (it.addr === msg.addr) {
                            let inst: Instance = {addr: '', online: false};
                            Object.assign(inst,it);
                            inst.unregistered = msg.unregistered;
                            instances.push(inst);
                        } else {
                            instances.push(it);
                        }
                    });
                    return instances;
                };
            })
        ).subscribe(this.instanceUpdates);
        this.instances = this.instanceUpdates.pipe(
            scan((old: Instance[], op: IInstancesOperation) => {
                return op(old);
            }, []),
            publishReplay(1),
            refCount()
        );
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
        if (history.items.length >= 2) {
            let c1: RequestCount = history.items[1];
            let c2: RequestCount = history.items[2];
            let count = c1.requestCount - c2.requestCount;
            let qps = count/60;
            let qpm = count;
            let tts = 0;
            let rate = 1;
            if (count>0) {
                tts = (c1.respondTime - c2.respondTime) / count;
                rate = (c1.succeedCount - c2.succeedCount) / count;
            }
            tts = Math.round(tts);
            rate = rate*1000 / 10;
            return {count:history.items[0].requestCount, qpm:qpm, tts:tts, succeedRate:rate};
        } else {
            return {count:0, qpm:0, tts:0, succeedRate: 100};
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

    public register(inst: Instance): void {
        this.api.register(inst.serviceName, inst.addr, inst.path).subscribe (
            (h: RestResult<string>) => {
                if (h.code == 0) {
                    this.updateReg.next({addr: inst.addr, unregistered: false});
                }
            }
        )
    }

    public unregister(inst: Instance): void {
        this.api.unregister(inst.serviceName, inst.addr).subscribe (
            (h: RestResult<string>) => {
                if (h.code == 0) {
                    this.updateReg.next({addr: inst.addr, unregistered: true});
                }
            }
        )
    }
}
