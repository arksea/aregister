import { Component, Inject, OnInit } from '@angular/core';
import { RestResult,ServiceNamespace,Service,ServiceVersion } from '../models'
import { ServiceDAO } from './service.dao';

@Component({
  selector: 'service-tree',
  templateUrl: './service-tree.component.html'
})
export class ServiceTreeComponent {
    serviceTree =  this.serviceDao.serviceTree;

    constructor(private serviceDao: ServiceDAO) {
    }

    ngOnInit(): void {
        this.updateServiceTree();
    }

    onClickRefreshBtn() {
        this.updateServiceTree();
    }

    updateServiceTree() {
        this.serviceDao.updateServiceTree();
    }

    onClickOneService(svc: ServiceVersion) {
        this.serviceDao.selectService(svc.regname);
    }
}

