package com.example.Controller;

import com.example.annotation.DAutowire;
import com.example.annotation.DController;
import com.example.annotation.DRequestMapping;
import com.example.annotation.DRequestParame;
import com.example.service.interfaces.IHelloService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author : fayne
 * @date : 2020-08-21
 **/
@DController("/test")
public class HelloController {
    @DAutowire("hello")
    private IHelloService iHelloService;


    @DRequestMapping("/hello")
    public void hello(HttpServletRequest req, HttpServletResponse resp, @DRequestParame("name") String name, @DRequestParame("id") String id){
        String result = iHelloService.hello() + "my name is " + name + ",id is " + id;
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
