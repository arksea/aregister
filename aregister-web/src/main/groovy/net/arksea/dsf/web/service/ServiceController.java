package net.arksea.dsf.web.service;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import net.arksea.dsf.DSF;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.restapi.RestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/5/24.
 */
@RestController
@RequestMapping(value = "/api/v1/services")
public class ServiceController {
    private static final String MEDIA_TYPE = "application/json; charset=UTF-8";
    private Logger logger = LogManager.getLogger(ServiceController.class);

    @Autowired
    private RegisterClient registerClient;
    @Resource(name = "restapiSystem")
    private ActorSystem restapiSys;

    @Resource(name = "serviceClientSystem")
    private ActorSystem svcClientSys;

    private JsonFormat.Printer printer =  JsonFormat.printer().includingDefaultValueFields();

    @RequestMapping(path = "list", method = RequestMethod.GET, produces = MEDIA_TYPE)
    public DeferredResult<String> getServiceList(final HttpServletRequest httpRequest) {
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");
        registerClient.getServiceList(10000).onComplete(
            new OnComplete<DSF.ServiceList>() {
                @Override
                public void onComplete(Throwable failure, DSF.ServiceList list) throws Throwable {
                    if (failure == null) {
                        try {
                            String json = printer.print(list);
                            result.setResult(RestUtils.createJsonResult(0, json, reqid));
                        } catch (Exception ex) {
                            logger.warn("Format service list failed", ex);
                            result.setErrorResult(RestUtils.createError(1, ex.getMessage(), reqid));
                        }
                    } else {
                        logger.warn("Get service list failed", failure);
                        result.setErrorResult(RestUtils.createError(1, failure.getMessage(), reqid));
                    }
                }
            }, restapiSys.dispatcher());
        return result;
    }

    @RequestMapping(path = "tree", method = RequestMethod.GET, produces = MEDIA_TYPE)
    public DeferredResult<String> getServiceTree(final HttpServletRequest httpRequest) {
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");
        registerClient.getServiceList(10000).onComplete(
            new OnComplete<DSF.ServiceList>() {
                @Override
                public void onComplete(Throwable failure, DSF.ServiceList list) throws Throwable {
                    if (failure == null) {
                        try {
                            String json = ServiceView.renderServiceTree(list);
                            result.setResult(RestUtils.createJsonResult(0, json, reqid));
                        } catch (Exception ex) {
                            logger.warn("Format service tree failed", ex);
                            result.setErrorResult(RestUtils.createError(1, ex.getMessage(), reqid));
                        }
                    } else {
                        logger.warn("Get service tree failed", failure);
                        result.setErrorResult(RestUtils.createError(1, failure.getMessage(), reqid));
                    }
                }
            }, restapiSys.dispatcher());
        return result;
    }

    @RequestMapping(path = "{name}/runtime", method = RequestMethod.GET, produces = MEDIA_TYPE)
    public DeferredResult<String> getService(
                @PathVariable("name") final String servieName,
                final HttpServletRequest httpRequest) {
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");
        registerClient.getService(servieName, 10000).onComplete(
            new OnComplete<DSF.Service>() {
                @Override
                public void onComplete(Throwable failure, DSF.Service service) throws Throwable {
                    if (failure == null) {
                        try {
                            String json = printer.print(service);
                            result.setResult(RestUtils.createJsonResult(0, json, reqid));
                        } catch (InvalidProtocolBufferException ex) {
                            logger.warn("Format service runtime failed: {}", servieName, ex);
                            result.setErrorResult(RestUtils.createError(1, ex.getMessage(), reqid));
                        }
                    } else {
                        logger.warn("Get service runtime failed: {}", servieName, failure);
                        result.setErrorResult(RestUtils.createError(1, failure.getMessage(), reqid));
                    }
                }
            }, restapiSys.dispatcher());
        return result;
    }

    @RequestMapping(path = "request", method = RequestMethod.GET, produces = MEDIA_TYPE)
    public DeferredResult<String> getServiceRequestCount(
        @RequestParam("path") final String servicePath,
        final HttpServletRequest httpRequest) {
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");

        DSF.GetRequestCountHistory msg = DSF.GetRequestCountHistory.getDefaultInstance();
        ActorSelection service = svcClientSys.actorSelection(servicePath);
        Patterns.ask(service, msg, 10000)
            .mapTo(classTag(DSF.RequestCountHistory.class))
            .onComplete(
            new OnComplete<DSF.RequestCountHistory>() {
                @Override
                public void onComplete(Throwable failure, DSF.RequestCountHistory his) throws Throwable {
                    if (failure == null) {
                        try {
                            String json = printer.print(his);
                            result.setResult(RestUtils.createJsonResult(0, json, reqid));
                        } catch (InvalidProtocolBufferException ex) {
                            logger.warn("Format request count failed: {}", servicePath, ex);
                            result.setErrorResult(RestUtils.createError(1, ex.getMessage(), reqid));
                        }
                    } else {
                        logger.warn("Get request count failed: {}", servicePath, failure);
                        result.setErrorResult(RestUtils.createError(1, failure.getMessage(), reqid));
                    }
                }
            }, restapiSys.dispatcher());
        return result;
    }

    @RequestMapping(path = "register/{name}/{addr}/", method = RequestMethod.DELETE, produces = MEDIA_TYPE)
    public DeferredResult<String> unregisterService(
        @PathVariable("name") final String serviceName,
        @PathVariable("addr") final String serviceAddr,
        final HttpServletRequest httpRequest) {
        logger.info("register {}@{}", serviceName, serviceAddr);
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");
        registerClient.unregisterAtRepertory(serviceName, serviceAddr, 10000).onComplete(
            new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean succeed) throws Throwable {
                    if (failure == null) {
                        result.setResult(RestUtils.createJsonResult(0, "true", reqid));
                    } else {
                        logger.warn("Unregister service failed: {}@{}", serviceName, serviceAddr, failure);
                        result.setErrorResult(RestUtils.createError(1, failure.getMessage(), reqid));
                    }
                }
            }, restapiSys.dispatcher());
        return result;
    }

    @RequestMapping(path = "register/{name}/{addr}/", method = RequestMethod.PUT, produces = MEDIA_TYPE)
    public DeferredResult<String> registerService(
        @PathVariable("name") final String serviceName,
        @PathVariable("addr") final String serviceAddr,
        @RequestParam("path") final String servicePath,
        final HttpServletRequest httpRequest) {
        logger.info("unregister {}@{} : {}", serviceName, serviceAddr, servicePath);
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");
        registerClient.registerAtRepertory(serviceName, serviceAddr, servicePath,10000).onComplete(
            new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean succeed) throws Throwable {
                    if (failure == null) {
                        result.setResult(RestUtils.createResult(0, reqid));
                    } else {
                        logger.warn("Register service failed: {}@{}", serviceName, serviceAddr, failure);
                        result.setErrorResult(RestUtils.createError(1, failure.getMessage(), reqid));
                    }
                }
            }, restapiSys.dispatcher());
        return result;
    }
}
