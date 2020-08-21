package com.example;

import com.example.annotation.*;
import org.apache.tomcat.util.buf.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author : fayne
 * @date : 2020-08-18
 **/
public class DDispatcherServlet extends HttpServlet {

    private Map<String, Object> ioc = new HashMap<>();
    private Map<String, Method> handleMap = new HashMap<>();
    private Properties contextConfig = new Properties();
    private List<String> classNames = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6.调用具体方法
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception detail :" + Arrays.toString(e.getStackTrace()));
        }
    }

    public void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        //获取请求uri信息
        String uri = req.getRequestURI();
        //获取请求上下文信息
        String contextPath = req.getContextPath();
        //转换成handleMap中key的url格式
        String url = ("/" + uri).replaceAll(contextPath,"").replaceAll("/+","/");
        //获取请求参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        if(!handleMap.containsKey(url)){
            resp.getWriter().write("404 Url not found !");
            return;
        }
        Method method = handleMap.get(url);
        //转换成ioc中key的格式
        String beanName = toLowerFristCase(method.getDeclaringClass().getSimpleName());
        //写死参数  parameterMap.get("name")[0]  每个参数可以传多个 这里写死去第一个
//        method.invoke(ioc.get(beanName),new Object[]{req,resp,parameterMap.get("name")[0],parameterMap.get("id")[0]});
        //动态取参数
        //获取方法参数的所有类型
        Class<?>[] parameterTypes = method.getParameterTypes();
        //实参
        Object[] parameValues = new Object[parameterTypes.length];
        //获取方法参数上的所有注解
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        //遍历所有参数
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if(parameterType == HttpServletResponse.class){
                parameValues[i] = resp;
            }else if(parameterType == HttpServletRequest.class){
                parameValues[i] = req;
            }else if(parameterType == String.class){
                //给带DRequestParame注解的参数赋值
                for(Annotation a : parameterAnnotations[i]){
                    if(a instanceof DRequestParame){
                        String value = ((DRequestParame) a).value();
                        if(!"".equals(value)){
                            parameValues[i] = Arrays.toString(parameterMap.get(value));
                        }
                    }
                }
            }else{
                parameValues[i] = null;
            }
        }
        method.invoke(ioc.get(beanName),parameValues);
    }

    @Override
    public void init(ServletConfig config) {
        //1.读取配置文件
        doLocalConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描类
        doScanner(contextConfig.getProperty("scan.page"));

        //3.实例化类，并放入IOC缓存
        doInstance();

        //4.完成依赖注入  注入变量值
        doAutowrite();

        //4.初始化HandleMapping
        doInitHandleMapping();

        System.out.println(" init end .");
    }

    private void doInitHandleMapping() {
        if(ioc.isEmpty()){
            return;
        }
        for(String key: ioc.keySet()){
            Class<?> aClass = ioc.get(key).getClass();

            if(!aClass.isAnnotationPresent(DController.class)){continue;}
            String baseUrl = aClass.getAnnotation(DController.class).value();
            String url= "";
            //controller也包含DRequestMapping注解

            if(aClass.isAnnotationPresent(DRequestMapping.class)){
                baseUrl = "/" + aClass.getAnnotation(DRequestMapping.class).value();
            }

            Method[] methods = aClass.getMethods();
            for(Method method : methods){
                if(!method.isAnnotationPresent(DRequestMapping.class)){continue;}
                DRequestMapping requestMapping = method.getAnnotation(DRequestMapping.class);
                url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                handleMap.put(url,method);
                System.out.println(url + ":" + url + ",method:" + method);
            }
        }
    }

    private void doAutowrite() {
        if (ioc.isEmpty()) {
            return;
        }
        for (String key : ioc.keySet()) {
            Class<?> aClass = ioc.get(key).getClass();
            //获取ioc实例化对象的所有字段 包含private/pubilc/protect/defaule
            Field[] declaredFields = aClass.getDeclaredFields();
            for (Field field : declaredFields) {
                //判断是否包含DAutowire注解
                if (!field.isAnnotationPresent(DAutowire.class)) {
                    continue;
                }
                field.setAccessible(true);
                //获取DAutowire注解对应的beanname
                DAutowire annotation = field.getAnnotation(DAutowire.class);
                String beanName = annotation.value().trim();
                if ("".equals(annotation.value())) {
                    beanName = field.getType().getTypeName();
                }
                try {
                    field.set(ioc.get(key), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }

        }


    }

    private String toLowerFristCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doInstance() {
        if (null == classNames) {
            return;
        }
        for (String clazz : classNames) {
            try {
                String beanName = "";
                Class<?> aClass = Class.forName(clazz);

                if (aClass.isAnnotationPresent(DController.class)) {
                    //获取类名，首字母小写
                    beanName = toLowerFristCase(aClass.getSimpleName());
                    Object instance = aClass.newInstance();
                    ioc.put(beanName, instance);
                } else if (aClass.isAnnotationPresent(DService.class)) {
                    //获取类名，首字母小写
                    beanName = toLowerFristCase(aClass.getSimpleName());
                    Object instance = aClass.newInstance();
                    //不同包下，相同类名
                    DService annotation = aClass.getAnnotation(DService.class);
                    if (!"".equals(annotation.value())) {
                        beanName = annotation.value();
                    }
                    ioc.put(beanName, instance);
                    //如果是接口，用他的实现类
                    for (Class<?> c : aClass.getInterfaces()) {
                        if (ioc.keySet().contains(c.getName())) {
                            throw new Exception("The bean is exist!!");
                        }
                        //匹配接口类型
                        ioc.put(c.getName(), instance);
                    }

                } else {
                    continue;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doScanner(String property) {
        URL url = this.getClass().getClassLoader().getResource("/" + property.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(property + "." + file.getName());
            } else {
                //取反，减少代码嵌套
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                //获取全类名
                String className = property + "." + file.getName().replaceAll(".class", "");
                classNames.add(className);

            }
        }
    }

    private void doLocalConfig(String servletContext) {
        //classpath去找application.properties配置文件，并读取出来
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream(servletContext);

        try {
            contextConfig.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (resource != null) {
                try {
                    resource.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
