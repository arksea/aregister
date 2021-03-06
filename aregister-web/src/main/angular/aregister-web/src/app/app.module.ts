import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule} from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HttpClientModule }     from '@angular/common/http';
import { LocationStrategy, HashLocationStrategy } from '@angular/common';
import { AppNotifyDialogComponent } from './app-notify-dialog.component';
import { AppNotifyDialogService } from './app-notify-dialog.service';

import { ClarityModule } from "clarity-angular";

import { AppComponent } from './app.component';

import { ServiceTreeComponent } from './service/service-tree.component';
import { ServiceComponent } from './service/service.component';
import { ServiceDAO } from './service/service.dao';

import { SystemStatusBarComponent } from './system/status-bar.component';
import { SystemDAO } from './system/system.dao';

import { ServiceAPI } from './service/service.restapi';

const appRoutes: Routes = [
  { path: '', redirectTo: 'services/', pathMatch: 'full' },
  { path: 'services/:regname', component: ServiceComponent }
];

@NgModule({
  declarations: [
    AppComponent,
    AppNotifyDialogComponent,
    ServiceTreeComponent,
    ServiceComponent,
    SystemStatusBarComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    RouterModule.forRoot(appRoutes,{ enableTracing: true }),
    ClarityModule,
    HttpClientModule,
    RouterModule.forRoot(appRoutes, { enableTracing: true })
  ],
  providers: [
    AppNotifyDialogService,
    { provide: LocationStrategy, useClass: HashLocationStrategy }, //自动在路由路径前添加#号，部署到Tomcat需要做此转换
    ServiceAPI, ServiceDAO, SystemDAO
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
