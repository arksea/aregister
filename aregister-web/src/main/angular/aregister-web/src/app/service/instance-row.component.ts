import { Component, Inject, OnInit, Input } from '@angular/core';
import { Router, ActivatedRoute, ParamMap,NavigationEnd  } from '@angular/router';
import { ServiceDAO } from './service.dao';
import { ServiceNamespace,Service,RestResult,Instance,Quality,
         Subscriber,RequestCountHistory,RequestCount } from '../models';

@Component({
  selector: 'instance-row',
  inputs: ['instance','quality'],
  templateUrl: './instance-row.component.html'
})
export class InstanceRowComponent {
    instance: Instance;
    quality: Quality;

    constructor(private serviceDao: ServiceDAO) {}

    updateRquestCount(i): void {
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
}
