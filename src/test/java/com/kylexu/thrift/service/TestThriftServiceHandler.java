package com.kylexu.thrift.service;

import org.apache.thrift.TException;

/**
 * 
 * @author javamonk
 * @createTime 2014年7月4日 下午4:59:26
 */
public class TestThriftServiceHandler implements TestThriftService.Iface {

    @Override
    public String echo(String message) throws TException {
        return message;
    }

}
