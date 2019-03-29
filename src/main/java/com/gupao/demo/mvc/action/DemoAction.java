package com.gupao.demo.mvc.action;

import com.gupao.demo.service.impl.IDemoService;
import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapper;
import com.gupaoedu.mvcframework.annotation.GPRequestParameter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Administrator on 2019/3/29.
 */
@GPController
@GPRequestMapper("/demo")
public class DemoAction {
    @GPAutowired
    private IDemoService demoService;
    @GPRequestMapper("/query")
    public void query(HttpServletRequest rqs , HttpServletResponse rspe, @GPRequestParameter("name") String name){
    String result = demoService.get(name);
        try {
            rspe.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GPRequestMapper("/add")
    public void add(HttpServletRequest rqs,HttpServletResponse reps,@GPRequestParameter("a")Integer a,@GPRequestParameter("b") Integer b){

        try {
            reps.getWriter().write(a+"+"+b+"="+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @GPRequestMapper("/remove")
    public void remove(HttpServletRequest rqs,HttpServletResponse reps,@GPRequestParameter("id") Integer id){}

}
