package com.wade.zkcleaner;

import com.wade.zkclient.IZkClient;
import com.wade.zkclient.ZkClient;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Copyright: (c) 2017 Asiainfo
 *
 * @desc:
 * @auth: steven.zhou
 * @date: 2017/09/14
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class);

    private static final ExecutorService executorService = Executors.newFixedThreadPool(100);
    private static final Map<String, Integer> STATS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.boot();
        String jarDirectory = bootstrap.getStartJarDirectory().getAbsolutePath();

        String defaultZooCfgPath = jarDirectory + File.separator + ".." + File.separator + "etc" + File.separator + "zoo.cfg";
        String zooCfgPath = System.getProperty("wade.ops.zoo.cfg.path", defaultZooCfgPath);
        LOG.info("加载的zoo.cfg文件位于: " + zooCfgPath);

        Properties prop = new Properties();
        FileInputStream fis = new FileInputStream(new File(zooCfgPath));
        prop.load(fis);
        String clientPort = prop.getProperty("clientPort");
        StringBuilder sb = new StringBuilder();

        Enumeration e = prop.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            if (key.startsWith("server.")) {
                String value = prop.getProperty(key);
                int iColon = value.indexOf(':');
                String ip = value.substring(iColon);
                sb.append(ip).append(clientPort).append(',');
            }
        }

        String zkAddr = sb.toString().substring(0, sb.length() - 1);
        LOG.info("zookeeper地址为: " + zkAddr);

        IZkClient zkClient = new ZkClient(zkAddr, 6000, 5000);

        List<Future> futures = new ArrayList<Future>();

        while (true) {

            Thread.sleep(5000);

            List<String> centerNames = zkClient.getChildren("/wade-relax/center");

            for (String centerName : centerNames) {
                String basepath = "/wade-relax/center/" + centerName + "/instances";
                List<String> instances = zkClient.getChildren(basepath);

                for (String instance : instances) {
                    LOG.info(centerName + ", 探测实例地址: " + instance);
                    Future<CheckResult> future = executorService.submit(new Worker(instance, basepath));
                    futures.add(future);
                }

            }

            for (int i = 0; i < futures.size(); i++) {
                CheckResult checkResult = (CheckResult) futures.get(i).get();

                if (checkResult.isHealth) {
                    STATS.put(checkResult.nodepath, 0);
                } else {
                    if (STATS.containsKey(checkResult.nodepath)) {
                        int cnt = STATS.get(checkResult.nodepath);
                        if (2 <= cnt) {
                            zkClient.delete(checkResult.nodepath);
                            STATS.put(checkResult.nodepath, 0);
                        } else {
                            STATS.put(checkResult.nodepath, cnt + 1);
                        }
                    }
                }

            }
        }
    }

}
