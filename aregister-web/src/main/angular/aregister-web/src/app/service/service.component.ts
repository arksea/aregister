import { Component, Inject, OnInit, Input } from '@angular/core';
import { Router, ActivatedRoute, ParamMap,NavigationEnd  } from '@angular/router';
import { ServiceDAO } from './service.dao';
import { Instance } from '../models';

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
                private route: ActivatedRoute) {
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

    onRegisterClick(i: Instance): void {
        if (i.unregistered) {
        } else {
          this.serviceDao.unregister(i);
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
