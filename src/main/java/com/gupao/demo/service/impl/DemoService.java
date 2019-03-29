package com.gupao.demo.service.impl;

/**
 * Created by Administrator on 2019/3/29.
 */
public class DemoService  implements  IDemoService{
    public String get(String name) {
        return "My name is" +name;
    }
}
