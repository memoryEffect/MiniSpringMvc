package com.gupaoedu.mvcframework.v1.servlet;

import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapper;
import com.gupaoedu.mvcframework.annotation.GPService;
import com.sun.deploy.net.HttpResponse;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by Administrator on 2019/3/29.
 */
public class GPDispatcherServlet extends HttpServlet {
    private Map<String, Object> mapping = new HashMap<String, Object>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!this.mapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = (Method) this.mapping.get(url);
        Map<String, String[]> params = req.getParameterMap();
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()), new Object[]{req, resp, params.get("name")[0]});

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        InputStream is = null;
        try {
            Properties configContext = new Properties();
            //加载配置文件
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
            configContext.load(is);
            //扫描相关的类
            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
            for (String className : mapping.keySet()) {
                if(!className.contains(".")){continue;}
                Class<?> clazz=Class.forName(className);
                //什么样的类才需要初始化呢？
                //加了注解的类，才初始化，怎么判断？
                if(clazz.isAnnotationPresent(GPController.class)){
                    //先看这个类有没有用注解controller
                    //加入IOC容器中
                    mapping.put(className,clazz.newInstance());
                    String baseUrl="";
                    if(clazz.isAnnotationPresent(GPRequestMapper.class)){
                        //有注解GPRequestMapper的类
                        GPRequestMapper requestMapper=clazz.getAnnotation(GPRequestMapper.class);
                        //取这个注解的值
                        baseUrl=requestMapper.value();
                    }
                    //用反射获取到所有方法
                    Method[] methods =clazz.getMethods();
                    for(Method method:methods){
                        //如果方法上有注解@GPRequestMapper
                        if(method.isAnnotationPresent(GPRequestMapper.class)){continue;}
                        //取这个注解的值
                        GPRequestMapper requestMapper=method.getAnnotation(GPRequestMapper.class);

                        String url=(baseUrl+"/"+requestMapper.value().replaceAll("/+","/"));
                        //把url放入容器
                        mapping.put(url,method);
                        System.out.println("method" +url +","+method);
                    }
                }else if(clazz.isAnnotationPresent(GPService.class)){
                    //获取GPService上的变量
                    GPService service=clazz.getAnnotation(GPService.class);
                    String beanName=service.value();
                    //如果值是空的
                    if("".equals(beanName)){
                        //直接用反射获取名称
                        beanName=clazz.getName();
                        //创建新的实例
                        Object instance = clazz.newInstance();
                        //放入容器
                        mapping.put(beanName,instance);
                        //遍历反射获取到的所有接口
                        for(Class<?> i:clazz.getInterfaces()){

                            mapping.put(i.getName(),instance);
                        }
                    }
                }else{
                    continue;
                }
            }
            //自动依赖注入
            for(Object object:mapping.values()){
                if(object==null){continue;}
                Class clazz=object.getClass();
                if(clazz.isAnnotationPresent(GPController.class)){
                    //Declared 所有的，特定的字段，包括private/protected/default
                    //正常来说，普通的oop只能拿到public修饰的属性
                    Field[] fields=clazz.getDeclaredFields();
                    for(Field field:fields){
                        if(!field.isAnnotationPresent(GPAutowired.class)){
                            continue;
                        }
                        GPAutowired autowired=field.getAnnotation(GPAutowired.class);
                        //如果用户没有定义beanName，默认就根据类型注入
                        String beanName =autowired.value();
                        if("".equals(beanName)){
                            //获得接口的类型，作为key待会拿这个key到ioc容器中去取值
                            beanName =field.getType().getName();
                            //如果是public以外的修饰符，只要加了@autowirted注解，都要强制赋值
                            //暴力访问
                            field.setAccessible(true);
                            try {
                                //用反射机制，动态给字段赋值
                                field.set(mapping.get(clazz.getName()),mapping.get(beanName));
                            }catch (IllegalAccessException e){
                                e.printStackTrace();
                            }

                        }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(is!=null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("GP MVC Framework is init");
    }

    private void doScanner(String scanPackage) {
        //scanPackage=com.gupao.demo,存储的是包路径
        //转换为文件路径，实际上就是把.替换为/就ok了
        //classPath
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file:classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage+"."+ file.getName());

            }else{
                if(!file.getName().endsWith(".class")){continue;}
                String clazzName =(scanPackage +"."+file.getName().replace(".class",""));
                mapping.put(clazzName,null);

            }
        }
    }

}
