package net.arksea.dsf.register;

import akka.dispatch.OnComplete;
import net.arksea.httpclient.asker.FuturedHttpClient;
import net.arksea.httpclient.asker.HttpAsk;
import net.arksea.httpclient.asker.HttpAskBuilder;
import net.arksea.httpclient.asker.HttpResult;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * Created by xiaohaixing on 2019/2/21.
 */
public class ServiceStateLogger {

    private static final Logger logger = LogManager.getLogger(ServiceStateLogger.class);
    private final int askTimeout;
    private final FuturedHttpClient logHttpClient;
    private final String postUrl;
    private long lastLogErrorTime = 0;
    private long lastLogSucceedTime = 0;

    public ServiceStateLogger(FuturedHttpClient client, String url, int timeout) {
        this.postUrl = url;
        this.askTimeout = timeout;
        this.logHttpClient = client;
    }

    public void write(String lines) {
        HttpPost post = new HttpPost(postUrl);
        post.setEntity(new StringEntity(lines, "UTF-8"));
        HttpAsk ask = new HttpAskBuilder(post).addSuccessCodes(200).addSuccessCodes(204).setTag("request").build();
        logHttpClient.ask(ask, askTimeout).onComplete(
            new OnComplete<HttpResult>() {
                @Override
                public void onComplete(Throwable ex, HttpResult ret) throws Throwable {
                    if (ex == null) {
                        if (needLogSucceed()) {
                            logger.info("Write service state logs succeed");
                        }
                    } else if (needLogError()) {
                        logger.warn("Write service state logs failed", ex);
                    }
                }
            }, logHttpClient.system.dispatcher()
        );
    }

    private boolean needLogError() {
        long now = System.currentTimeMillis();
        if (now - lastLogErrorTime > 600_000) {
            lastLogErrorTime = now;
            lastLogSucceedTime = 0;
            return true;
        } else {
            return false;
        }
    }

    private boolean needLogSucceed() {
        long now = System.currentTimeMillis();
        if (lastLogSucceedTime == 0) {
            lastLogSucceedTime = now;
            lastLogErrorTime = 0;
            return true;
        } else {
            return false;
        }
    }
}
