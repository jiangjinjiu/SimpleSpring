package com.example.service.impl;

import com.example.annotation.DService;
import com.example.service.interfaces.IHelloService;

/**
 * @author : fayne
 * @date : 2020-08-21
 **/
@DService("hello")
public class HelloServiceImpl implements IHelloService {
    @Override
    public String hello() {
        return "Hell world!";
    }
}
