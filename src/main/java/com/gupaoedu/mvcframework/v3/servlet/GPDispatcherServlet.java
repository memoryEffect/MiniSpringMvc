package com.gupaoedu.mvcframework.v3.servlet;

import com.gupaoedu.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Administrator on 2019/3/30.
 */
public class GPDispatcherServlet extends HttpServlet {
    //保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();

    //保存扫描所有的类名
    private List<String> classNames = new ArrayList<String>();

    //传说中的IOC容器，我们来揭开它的神秘面纱
    //为了简化程序，暂时不考虑ConcureentHashMap
    //主要还是关注设计思想和原理
    //注册式单例体现
     private Map<String,Object> ioc =new HashMap<String, Object>();

    //保存url和method对应关系
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6.运行调用阶段
        try {
            //doPost()方法中，用了委派模式，委派模式的具体逻辑在 doDispatch()方法中：
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exection,Detail :" +Arrays.toString(e.getStackTrace()));
        }

    }
    //url参数处理还是静态代码
    /*private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url =req.getRequestURI();
        String contextPath=req.getContextPath();
        url=url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handleMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
        }
        Method method=this.handleMapping.get(url);
        //第一个参数：方法所在实例
        //第二个参数：调用时所需的实参
        Map<String,String[]> params=req.getParameterMap();
        String beanName= toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});


    }*/

    /**
     * 匹配url
     * @param req
     * @param resp
     * @throws Exception
     */
    //url参数的动态获取
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
       Handler handler=getHandle(req);
        if(handler==null){
            resp.getWriter().write("404 Not Found");
            return;
        }
        //获得方法的形参列表
        Class<?>[] paramTypes=handler.method.getParameterTypes();
        //保存所有需要自动赋值的参数值
        Object[] paramValues= new Object[paramTypes.length];
        Map<String,String[]> params=req.getParameterMap();
        for(Map.Entry<String,String[]> parm:params.entrySet()){
            String value= Arrays.toString(parm.getValue()).replaceAll("\\[|\\]","").replaceAll("\\s",",");
            //如果找到匹配的对象，则开始填充参数值
            if(!handler.paramIndexMapping.containsKey(parm.getKey())){continue;}
            int index =handler.paramIndexMapping.get(parm.getKey());
            paramValues[index] = convert(paramTypes[index],value);
        }
        if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
            int respIndex=handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex]=resp;
        }
        if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
            int repIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[repIndex]=req;
        }
        Object returnValue=handler.method.invoke(handler.controller,paramValues);
        if(returnValue==null||returnValue instanceof Void){
            return;
        }
        resp.getWriter().write(returnValue.toString());
    }

    private Handler getHandle(HttpServletRequest req) {
        if(handlerMapping.isEmpty()){return null;}
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url=url.replace(contextPath,"").replaceAll("/+","/");
        for(Handler handler :handlerMapping){
            Matcher matcher =handler.pattern.matcher(url);
            //如果没有匹配上继续下一个
            if(!matcher.matches()){continue;}
            return handler;
        }
        return null;
    }
    //url传过来的参数是String类型的,HTTP基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type,String value){
        //如果是int
        if(Integer.class==type){
            return  Integer.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该用策略模式
        return value;
    }
    //初始化阶段
    //按照实现思路先封装代码
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLocalConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3.初始化扫描的类，并且将它们放入到IOC容器之中
        doInstance();
        //4.完成依赖注入
        doAutowired();
        //5.初始化HandleMapping
        initHandleMapping();
    }



    //加载配置文件
    private void doLocalConfig(String contextConfigLocation) {
        //直接从类路径下找到Spring主配置文件所在的路径
        //并且将其读取出来放到properties对象中
        //相当于scanPackage=com.gupaoedu.demo,从文件种保存到内存中
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    //扫描的类
    private void doScanner(String scanPackage) {
        //scanPackage =com.gupaoedu.demo ，存储包路径
        //转换为文件路径，实际上就是把.替换为/就可以了
        //classpath
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classpath = new File(url.getFile());
        for (File file : classpath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                //文件名后缀是否.class
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(className);
            }
        }
    }

    //工厂模式的体现
    private void doInstance() {
        //初始化 ，为DI做准备
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                //什么样的类才需要初始化呢？
                //加了注解的类，才需要初始化，怎么判断？
                //为了简化代码逻辑，主要体会设计思想,只举例@GPController,@GPService
                //如果指定类型的注释存在于此类上则返回true
                if(clazz.isAnnotationPresent(GPController.class)){
                    Object instance = clazz.newInstance();
                    //spring默认类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(GPService.class)){
                    //1.自定义的beanName
                    GPService service = clazz.getAnnotation(GPService.class);
                    String beanName =service.value();
                    //2.默认首字母小写
                    if("".equals(beanName.trim())){
                        beanName=toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //3.根据类型自动赋值，投机取巧的方式
                    for(Class<?> i:clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The"+i.getName()+"is exists!!");
                        }
                        //把接口的类型直接当成key了
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //如果类名本身是小写字母，确实会出问题
    //但是我要说明的是:这个方法是我自己用，private的
    //传值也是自己传，类也都遵循了驼峰命名
    //默认传入的值，存在首字母小写的情况，也不可能出现非字母的情况

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        //之所以加，是因为大小写字母的ASCII码相差32位,
        //而且大写字母的ASCII码要小于小写字母的ASCII码
        //在Java中，对char做运算，实际上就是对ASCII码做运算
        chars[0]+=32;
        return String.valueOf(chars);
    }
    //自动依赖注入
    private void doAutowired() {
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry: ioc.entrySet()){
            //Declared 所有的，特定的字段，包括private/protected/default
            //正常来说，普通的oop编程只能拿到public属性
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(GPAutowired.class)){
                    continue;
                }
                GPAutowired autowired = field.getAnnotation(GPAutowired.class);
                //如果用户没有自定义beanName，默认就根据类型注入
                String beanName =autowired.value().trim();
                if(!"".equals(beanName)){
                    //获得接口的类型，作为key待会拿这个key到ioc容器中去取值
                    beanName =toLowerFirstCase(field.getType().getName());
                }
                //如果是public以外的修饰符，只要加@Autowired注解，都要强制赋值
                //暴力访问
                field.setAccessible(true);

                try {
                    //用反射机制，动态给字段赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //初始化url和Method的一对一对应关系
    //策略模式提现
    private void initHandleMapping() {
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz=entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(GPController.class)){continue;}

            //保存写在类上面的@GPRequestMapping("/demo");
            String baseUrl="";
            //获取Controller的url位置
            if(clazz.isAnnotationPresent(GPRequestMapper.class)){
                GPRequestMapper requestMapper=clazz.getAnnotation(GPRequestMapper.class);
                baseUrl = requestMapper.value();
            }

            //获取method的url配置
            Method[] methods = clazz.getMethods();

            //默认获得所有public方法
            for(Method method:methods){
                //没有加@GPRequestMapping注解的直接忽略
                if(!method.isAnnotationPresent(GPRequestMapper.class)){
                    continue;
                }
                //映射URL
                GPRequestMapper requestMapper=method.getAnnotation(GPRequestMapper.class);
                //优化
                // //demo //query
                String url = ("/"+baseUrl +"/"+requestMapper.value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(url);
                handlerMapping.add(new Handler(entry.getValue(),method,pattern));
                System.out.println("mapping" + url +","+method);
            }

        }
    }


}
