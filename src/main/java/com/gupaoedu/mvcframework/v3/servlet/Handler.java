package com.gupaoedu.mvcframework.v3.servlet;

import com.gupaoedu.mvcframework.annotation.GPRequestParameter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handler 记录Controller中的requestMpping和method的对应关系
 * 内部类
 */
public class Handler {
    protected  Object controller;//保存方法对应的实例
    protected Method method;//保存映射的方法
    protected Pattern pattern;
    protected Map<String,Integer> paramIndexMapping;//参数顺序

    public Handler(Object controller, Method method, Pattern pattern) {
        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
        paramIndexMapping = new HashMap<String, Integer>();
        putParamIndexMapping(method);
    }

    private void putParamIndexMapping(Method method) {
        //提取方法中加了注解的参数
        Annotation[][] pa = method.getParameterAnnotations();
        for(int i=0;i<pa.length;i++){
            for (Annotation a:pa[i]){
                if(a instanceof GPRequestParameter){
                    String paramName=((GPRequestParameter) a).value();
                    if(!"".equals(paramName.trim())){
                        paramIndexMapping.put(paramName,i);
                    }
                }
            }
        }
        //提取方法中的request和response参数
        Class<?>[] paramsType =method.getParameterTypes();
        for(int i=0;i<paramsType.length;i++){
            Class<?> type =paramsType[i];
            if(type== HttpServletRequest.class||type== HttpServletResponse.class){
                paramIndexMapping.put(type.getName(),i);
            }
        }
    }
}
