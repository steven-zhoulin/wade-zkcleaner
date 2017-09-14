package com.wade.zkcleaner;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.util.concurrent.Callable;

/**
 * Copyright: (c) 2017 Asiainfo
 *
 * @desc:
 * @auth: steven.zhou
 * @date: 2017/09/14
 */
public class Worker implements Callable<String> {

    private static final Logger LOG = Logger.getLogger(Worker.class);

    private String addr;
    private String probeURL;
    private String basepath;

    public Worker(String addr, String basepath) {
        this.addr = addr;
        this.basepath = basepath;
        this.probeURL = "http://" + addr + "/probe.jsp";
    }

    @Override
    public String call() throws Exception {

        String rtn = null;
        HttpGet request = new HttpGet(this.probeURL);

        try {

            HttpResponse response = HttpClients.createDefault().execute(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                LOG.debug(this.probeURL + " is running");
            } else {
                String content = EntityUtils.toString(response.getEntity());
                LOG.info("probe failure: " + this.probeURL + " " + content.substring(0, 50));
                rtn = this.basepath + "/" + this.addr;
            }

        } catch (Exception e) {

        }

        return rtn;
    }

}
