import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule} from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HttpClientModule }     from '@angular/common/http';
import { LocationStrategy, HashLocationStrategy } from '@angular/common';

import { ClarityModule } from "clarity-angular";

import { createStore, Store, StoreEnhancer } from 'redux';
import { AppStore } from './app-store';
import { AppState, rootReducer } from './app-state';
import { ServiceDAO } from './service/service.dao';

import { AppComponent } from './app.component';
import { ServiceTreeComponent } from './service/service-tree.component';
import { ServiceComponent } from './service/service.component';
import { InstanceRowComponent } from './service/instance-row.component';
import { SystemStatusBarComponent } from './system/status-bar.component';



import { ServiceAPI } from './service/service.restapi';

const devtools: StoreEnhancer<AppState> =
  window['devToolsExtension'] ? window['devToolsExtension']() : f => f;


export const store: Store<AppState> = createStore(rootReducer, devtools);

const appRoutes: Routes = [
  { path: '', redirectTo: 'services/', pathMatch: 'full' },
  { path: 'services/:regname', component: ServiceComponent }
];

@NgModule({
  declarations: [
    AppComponent,
    ServiceTreeComponent,
    ServiceComponent,
    InstanceRowComponent,
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
    { provide: LocationStrategy, useClass: HashLocationStrategy }, //自动在路由路径前添加#号，部署到Tomcat需要做此转换
    { provide: AppStore, useValue: store },
    ServiceAPI,
    ServiceDAO
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
