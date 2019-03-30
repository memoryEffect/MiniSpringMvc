package com.gupaoedu.mvcframework.v2.servlet;

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
    private Map<String,Method> handleMapping= new HashMap<String, Method>();

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
    //url参数的动态获取
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        //获得绝对路径
        String url = req.getRequestURI();
        //处理成相对路径
        String contextPath=req.getContextPath();
        url=url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handleMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
        }
        Method method = this.handleMapping.get(url);

        //从request中拿到url传过来的参数
        Map<String,String[]>params=req.getParameterMap();

        //获取方法的形参列表
        Class<?>[] parameterTypes= method.getParameterTypes();

        Object[] paramValues=new Object[parameterTypes.length];

        for(int i =0;i<paramValues.length;i++){
            Class parameterType=parameterTypes[i];
            //不能用instanceof,paramterType它不是实参，而是形参
            if(parameterType==HttpServletRequest.class){
                paramValues[i]=req;
                continue;
            }else if(parameterType==HttpServletResponse.class){
                paramValues[i]=resp;
                continue;
            }else if(parameterType==String.class){
                GPRequestParameter parameter=method.getAnnotation(GPRequestParameter.class);
                if(params.containsKey(parameter.value())){
                    for(Map.Entry<String,String[]> param:params.entrySet() ){
                        String value=Arrays.toString(param.getValue()).replaceAll("\\[|\\]","").replaceAll("\\s",",");
                        paramValues[i]=value;
                    }
                }
            }
        }
        //通过反射拿到 method所在 class，拿到 class之后还是拿到 class的名称
        //再调用 toLowerFirstCase获得 beanName
        String beanName= toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),paramValues);

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
            if(clazz.isAnnotationPresent(GPController.class)){continue;}

            //保存写在类上面的@GPRequestMapping("/demo");
            String baseUrl="";
            if(clazz.isAnnotationPresent(GPRequestMapper.class)){
                GPRequestMapper requestMapper=clazz.getAnnotation(GPRequestMapper.class);
                baseUrl = requestMapper.value();
            }
            //默认获得所有public方法
            for(Method method:clazz.getMethods()){
                if(!method.isAnnotationPresent(GPRequestMapper.class)){
                    continue;
                }
                GPRequestMapper requestMapper=clazz.getAnnotation(GPRequestMapper.class);
                //优化
                // //demo //query
                String url = ("/"+baseUrl +"/"+requestMapper.value()).replaceAll("/+","/");
                handleMapping.put(url,method);
                System.out.println("mapper"+url+","+method);
            }

        }
    }


}
