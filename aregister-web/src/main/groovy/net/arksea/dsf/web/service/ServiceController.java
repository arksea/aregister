package net.arksea.dsf.web.service;

import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import net.arksea.dsf.DSF;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.restapi.RestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import net.arksea.restapi.RestResult;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

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
    private ActorSystem system;

    @RequestMapping(method = RequestMethod.GET, produces = MEDIA_TYPE)
    public DeferredResult<String> getServiceList(final HttpServletRequest httpRequest) {
        DeferredResult<String> result = new DeferredResult<>();
        String reqid = (String)httpRequest.getAttribute("restapi-requestid");
        registerClient.getServiceList(10000).onComplete(
            new OnComplete<DSF.ServiceList>() {
                @Override
                public void onComplete(Throwable failure, DSF.ServiceList list) throws Throwable {
                    if (failure == null) {
                        try {
                            String json = JsonFormat.printer().print(list);
                            result.setResult(RestUtils.createJsonResult(0, json, reqid));
                        } catch (InvalidProtocolBufferException e) {
                            String err = "format service list failed";
                            logger.debug(err, failure);
                            result.setErrorResult(new RestResult(1, err, reqid));
                        }
                    } else {
                        String err = "get service list failed";
                        logger.debug(err, failure);
                        result.setErrorResult(new RestResult(1, err, reqid));
                    }
                }
            }, system.dispatcher());
        return result;
    }

}
