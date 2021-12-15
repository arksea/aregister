import { Component, Inject, OnInit, Input } from '@angular/core';
import { Router, ActivatedRoute, ParamMap,NavigationEnd  } from '@angular/router';
import { ServiceDAO } from './service.dao';
import { Instance } from '../models';
import { AppNotifyDialogService } from '../app-notify-dialog.service';

@Component({
  selector: 'service',
  templateUrl: './service.component.html'
})
export class ServiceComponent {
    serviceName = this.serviceDao.selectedService;
    instances = this.serviceDao.instances;
    subscribers = this.serviceDao.subscribers;

    constructor(private serviceDao: ServiceDAO,
                private router: Router,
                private route: ActivatedRoute,
                private notify: AppNotifyDialogService) {
    }

    ngOnInit(): void {
        let regname = this.route.snapshot.paramMap.get('regname')
        if (regname) {
            this.serviceDao.selectService(regname);
        }
    }


    updateRquestCount(i: Instance): void {
        this.serviceDao.updateRquestCount(i);
    }

    //格式化时间展示
    formatTime(time: number): string {
         if (time > 0) {
           let date: Date = new Date();
           date.setTime(time);
           return date.toLocaleString();
         } else {
           return '';
         }
    }

    formatSucceedRate(rate: number): string {
         return rate.toFixed(3);
    }

    onRegisterClick(i: Instance): void {
        if (i.unregistered) {
            this.notify.openWidthConfirm('注册实例', '确认要注册吗？操作将导入请求流量！', i.addr).subscribe(
                succeed => {
                    if (succeed) {
                        this.serviceDao.register(i);
                    }
                }
            );
        } else {
            this.notify.openWidthConfirm('注销实例', '确认要注销吗？操作将切除请求流量！', i.addr).subscribe(
                succeed => {
                    if (succeed) {
                        this.serviceDao.unregister(i);
                    }
                }
            );
        }
    }

    registerButtonText(i: Instance): string {
        if (i.unregistered) {
            return '注册';
        } else {
            return '注销';
        }
    }
}
