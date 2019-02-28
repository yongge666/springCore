package com.yongge.mvcframework.servlet;

import com.yongge.mvcframework.annotation.Autowired;
import com.yongge.mvcframework.annotation.Controller;
import com.yongge.mvcframework.annotation.RequestMapping;
import com.yongge.mvcframework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DispictherService extends HttpServlet {
    private Properties configContext = new Properties();
    private List<String> classNames = new ArrayList <String>();
    private Map<String,Object> ioc = new HashMap <String, Object>();
    private Map<String,Method> handdleMapping = new HashMap <String, Method>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       try {
           doDispach(req,resp);
       }catch (Exception e){
            resp.getWriter().write("500"+"detail"+Arrays.toString(e.getStackTrace()));
       }

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        loadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描加载相关的类
        scanner(configContext.getProperty("scanPackage"));

        //3.初始化IOC容器
        initIOC();

        //4.反射依赖注入（自动赋值）
        autowired();

        //5.构建handlerMapping
        initHandleMapping();

        System.out.println("init done");




    }

    private void doDispach(HttpServletRequest req, HttpServletResponse resp) throws ServletException,IOException {
        //拿到用户的请求
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");
        if (!this.handdleMapping.containsKey(url)){
            resp.getWriter().write("404 not found");
            return;
        }

        Method method = this.handdleMapping.get(url);
        String beanName = lowerFist(method.getDeclaringClass().getSimpleName());
        Map<String,String[]> params = req.getParameterMap();

        try {
            method.invoke(ioc.get(beanName),new Object []{req,resp,params.get("name")[0]});
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }


    }

    private void loadConfig(String config){
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(config);
        try {
            configContext.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (null != inputStream){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void scanner(String packageName){
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()){
            if (file.isDirectory()){
                scanner(packageName+"."+file.getName());;
            }else {
                if (!file.getName().endsWith(".class")){
                    continue;
                }
                String className = packageName + "." + file.getName().replace(".class","");
                classNames.add(className);
            }
        }

    }

    private void initIOC(){
        if (classNames.isEmpty()){
            return;
        }
        for (String className: classNames){
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)){
                    String beanName = clazz.getSimpleName();
                    try {
                        Object instance = clazz.newInstance();
                        ioc.put(lowerFist(beanName),instance);

                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }else if (clazz.isAnnotationPresent(Service.class)){

                    Service service = clazz.getAnnotation(Service.class);
                    //如果Service注解定义了beanName则使用注解的beanName
                    String beanName = service.value();
                    //普通类首字母小写
                    if ("".equals(beanName)){
                        beanName = lowerFist(clazz.getSimpleName());
                    }
                    Object instance = null;
                    try {
                        instance = clazz.newInstance();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    ioc.put(beanName,instance);

                    //Service可能包含接口，应该只初始化实现类，此时使用接口全类名作为key，方便依赖注入时使用
                    Class<?> [] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces){
                        String name = i.getName();
                        if (ioc.containsKey(name)){
                            try {
                                throw new Exception("The class" +clazz.getName()+ "bean name"+name+"exists");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        ioc.put(name,instance);
                    }

                }else {
                    continue;
                }

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    private String lowerFist(String str){
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);

    }
    private void autowired(){
        if (ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry : ioc.entrySet()){

            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields){
                if (!field.isAnnotationPresent(Autowired.class)){continue;}

                Autowired autowired = field.getAnnotation(Autowired.class);

                String beanNane = autowired.value();
                if ("".equals(beanNane)){
                    beanNane = field.getType().getName();
                }

                //对于private，procted和default字段设置可见
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(),ioc.get(beanNane));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    private void initHandleMapping(){
        if (ioc.isEmpty()){return;}
        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)){continue;}

            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)){
                RequestMapping requestMapping =  clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods){
                if (!method.isAnnotationPresent(RequestMapping.class)){ continue;}

                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = "/"+baseUrl + "/" + requestMapping.value();
                url = url.replaceAll("/+","/");
                handdleMapping.put(url,method);
                System.out.println("method:"+method+"url:"+url);



            }


        }
    }
}
