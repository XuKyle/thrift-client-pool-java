package com.kylexu.thrift;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.kylexu.thrift.service.TestThriftService;
import com.kylexu.thrift.service.TestThriftServiceHandler;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.server.TThreadPoolServer.Args;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author javamonk
 * @createTime 2014年7月4日 下午4:51:18
 */
public class TestThriftClientPool {

    private static Logger logger = LoggerFactory.getLogger(TestThriftClientPool.class);

    @BeforeClass
    public static void setUp() {
        int port = 9090;

        try {
            TServerTransport serverTransport = new TServerSocket(port);

            Args processor = new TThreadPoolServer.Args(serverTransport)
                    .inputTransportFactory(new TFramedTransport.Factory())
                    .outputTransportFactory(new TFramedTransport.Factory())
                    .processor(new TestThriftService.Processor<>(new TestThriftServiceHandler()));
            //            processor.maxWorkerThreads = 20;
            TThreadPoolServer server = new TThreadPoolServer(processor);

            logger.info("Starting test server...");
            new Thread(server::serve).start();
            Thread.sleep(1000); // waiting server init
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testReusingIFace() throws TException {
        List<ServiceInfo> serverList = Collections
                .singletonList(new ServiceInfo("127.0.0.1", 9090));
        PoolConfig config = new PoolConfig();
        config.setFailover(true);
        config.setTimeout(1000);
        ThriftClientPool<TestThriftService.Client> pool = new ThriftClientPool<>(serverList,
                transport -> new TestThriftService.Client(new TBinaryProtocol(new TFramedTransport(transport))),
                config);

        TestThriftService.Iface iface = pool.iface();
        iface.echo("Hello!");
        boolean exceptionThrow = false;
        try {
            iface.echo("Hello again!");
        } catch (IllegalStateException e) {
            logger.info("exception thrown expected!", e);
            exceptionThrow = true;
        }
        Assert.assertTrue("exception must thrown", exceptionThrow);
    }

    @Test
    public void testEcho() throws InterruptedException {
        List<ServiceInfo> serverList = Arrays.asList( //
                new ServiceInfo("127.0.0.1", 9092), //
                new ServiceInfo("127.0.0.1", 9091), //
                new ServiceInfo("127.0.0.1", 9090));

        PoolConfig config = new PoolConfig();
        config.setFailover(true);
        config.setTimeout(1000);
        config.setMinIdle(3);
        config.setMaxTotal(10);
        //        config.setBlockWhenExhausted(true);
        ThriftClientPool<TestThriftService.Client> pool = new ThriftClientPool<>(serverList,
                transport -> new TestThriftService.Client(new TBinaryProtocol(new TFramedTransport(transport))),
                config);
        // pool.setServices(serverList);

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 100; i++) {
            int counter = i;
            executorService.submit(() -> {
                try {
                    try (ThriftClient<TestThriftService.Client> client = pool.getClient()) {
                        TestThriftService.Iface iFace = client.iFace();
                        String response = iFace.echo("Hello " + counter + "!");
                        logger.info("get response: {}", response);
                        client.finish();
                    }
                } catch (Throwable e) {
                    logger.error("get client fail", e);
                }
            });

            executorService.submit(() -> {
                try {
                    TestThriftService.Iface iFace = pool.iface();
                    String response = iFace.echo("Hello " + counter + "!");
                    logger.info("get response: {}", response);
                } catch (Throwable e) {
                    logger.error("get client fail", e);
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
    }
}
