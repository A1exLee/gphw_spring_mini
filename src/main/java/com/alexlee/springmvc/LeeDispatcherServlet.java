package com.alexlee.springmvc;

import com.alexlee.springmvc.annotation.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * @author alexlee
 */
public class LeeDispatcherServlet extends HttpServlet {

    public static final char a = 'a';
    private Properties configProperties = new Properties();

    List<HandlerMapping> handlerMappings = Lists.newArrayList();

    List<String> classNames = Lists.newArrayList();
    Map<String, Object> beanDefinitionRegistry = Maps.newHashMap();

    @Override
    public void init(ServletConfig config) {
//        1.加载配置文件
        doLoadProperties(config.getInitParameter("contextConfigLocation"));
        if (configProperties.isEmpty()) {
            System.out.println("配置文件初始化失败");
            return;
        }
//        2.扫描package
        doScanPackage(configProperties.getProperty("scan.package"));
//        3.实例化bean并加入ioc容器
        doInstance();
//        4.依赖注入
        doAutowired();
//        5.初始化handlerMapping映射关系
        doInitHandlerMapping();
    }

    private void doLoadProperties(String propertiesPath) {
        InputStream is = LeeDispatcherServlet.class.getClassLoader().getResourceAsStream(propertiesPath);
        try {
            configProperties.load(is);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void doScanPackage(String packageName) {
        if (packageName.trim().isEmpty()) {
            System.out.println("扫描包名为空");
            return;
        }
        String url = packageName.replaceAll("\\.", "/");
        File file = new File(this.getClass().getClassLoader().getResource(url).getPath());
        if (file.exists()) {
            File[] files = file.listFiles();
            for (File f : files) {
                if (f.isDirectory()) {
                    doScanPackage(packageName + "." + f.getName());
                }
                if (f.getName().endsWith(".class")) {
                    classNames.add(packageName + "." + f.getName().split("\\.")[0]);
                }
            }
        } else {
            System.out.println("扫描的包不存在");
        }
    }

    private void doInstance() {
        classNames.forEach(clazz -> {
            try {
                Class<?> clz = Class.forName(clazz);
                if (clz.isAnnotationPresent(LeeComponent.class) || clz.isAnnotationPresent(LeeController.class)) {
                    Constructor constructor = clz.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object o = constructor.newInstance();
                    Class<?>[] interfaces = clz.getInterfaces();
                    for (Class<?> inter : interfaces) {
                        beanDefinitionRegistry.putIfAbsent(toFirstLowerCase(inter.getSimpleName()), o);
                    }
                    Class superClz = clz.getSuperclass();
//                    容器中给未添加LeeComponent父类保存子类的对象
                    while (Object.class != superClz) {
                        if (!superClz.isAnnotationPresent(LeeComponent.class) || clz.isAnnotationPresent(LeeController.class)) {
                            beanDefinitionRegistry.putIfAbsent(superClz.getSimpleName(), 0);
                            superClz = superClz.getSuperclass();
                        }
                    }
                    beanDefinitionRegistry.putIfAbsent(toFirstLowerCase(clz.getSimpleName()), o);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    /**
     * 首字母小写
     *
     * @param clazz
     * @return
     */
    private String toFirstLowerCase(String clazz) {
        char[] chars = clazz.toCharArray();
        if (chars[0] < a) {
            chars[0] += 32;
        }
        return new String(chars);

    }

    private void doAutowired() {
        beanDefinitionRegistry.forEach((key, value) -> {
            try {
                Class<?> clz = value.getClass();
                //           无参构造器，如果没有则无法使用@Autowired对set方法和字段赋值
                Constructor<?> constructorWithoutParam = clz.getDeclaredConstructor(null);
                Field[] fields = clz.getDeclaredFields();
                //          未使用Autowired的成员变量，保存起来，可能使用构造器或者方法注入
                Map<String, Field> unDIField = Maps.newHashMap();
//                1.直接注入
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.isAnnotationPresent(LeeAutowired.class)) {
                        if (constructorWithoutParam == null) {
                            throw new RuntimeException("Unsatisfied dependency expressed through constructor parameter 0");
                        }
                        doDI(value, field);
                    } else {
                        unDIField.put(field.getType().getName(), field);
                    }
                }
//                2.构造器注入
                Constructor<?>[] constructors = clz.getDeclaredConstructors();
                for (Constructor<?> constructor : constructors) {
                    if (constructor.isAnnotationPresent(LeeAutowired.class) && constructor.getParameterCount() == 1 && unDIField.containsKey(constructor.getParameterTypes()[0].getName())) {
                        Field field = unDIField.get(constructor.getParameterTypes()[0].getName());
                        doDI(value, field);
                        unDIField.remove(constructor.getParameterTypes()[0].getName());
                    }
                }
//              3.set注入
                Method[] methods = value.getClass().getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(LeeAutowired.class) && method.getParameterCount() == 1 && unDIField.containsKey(method.getParameterTypes()[0].getName())) {
                        Field field = unDIField.get(method.getParameterTypes()[0].getName());
                        doDI(value, field);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    private void doDI(Object obj, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object value = beanDefinitionRegistry.get(toFirstLowerCase(field.getType().getSimpleName()));
        Preconditions.checkNotNull(value, "field[" + field.getName() + "] could not be autowired:null");
        field.set(obj, value);
    }


    private void doInitHandlerMapping() {
        if (beanDefinitionRegistry.isEmpty()) {
            return;
        }
        beanDefinitionRegistry.entrySet().forEach(entry -> {
            Class clz = entry.getValue().getClass();
            if (clz.isAnnotationPresent(LeeController.class)) {
                String url = "";
                if (!clz.isAnnotationPresent(LeeRequestMapping.class)) {
                    return;
                }
                LeeRequestMapping annotation = (LeeRequestMapping) clz.getAnnotation(LeeRequestMapping.class);
                url += annotation.value().replaceAll("/+", "/");
                Method[] methods = clz.getDeclaredMethods();
                for (Method method : methods) {
                    if (!method.isAnnotationPresent(LeeRequestMapping.class)) {
                        continue;
                    }
                    LeeRequestMapping mapping = method.getAnnotation(LeeRequestMapping.class);
                    url += mapping.value().replaceAll("/+", "/");
                    Pattern pattern = Pattern.compile(url);
                    handlerMappings.add(new HandlerMapping(pattern, method, entry.getValue()));
                }
            }
        });

    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doGet(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String url = request.getRequestURI().replace(request.getContextPath(), "").replaceAll("/+", "/");
        if ("/".equals(url)) {
            response.getWriter().write("hello");
            return;
        }
        HandlerMapping handlerMapping = getHandlerMapping(url);
        if (handlerMapping == null) {
            response.getWriter().write("404 Not Found");
            return;
        }
//        参数列表
        Map<String, Integer> paramIndex = handlerMapping.getParamIndex();
        Object[] param = new Object[paramIndex.keySet().size()];
        for (Map.Entry<String, Integer> entry : paramIndex.entrySet()) {
            if (HttpServletResponse.class.getName().equals(entry.getKey())) {
                param[entry.getValue()] = response;
            } else if (HttpServletRequest.class.getName().equals(entry.getKey())) {
                param[entry.getValue()] = request;
            } else {
                String paramValue=request.getParameter(entry.getKey());
                if(paramValue==null||paramValue.trim().length()<1){
                    response.getWriter().write("param "+entry.getKey()+" not exist!");
                    return;
                }
                param[entry.getValue()] = paramValue;
            }
        }
        try {
            handlerMapping.getMethod().invoke(handlerMapping.getController(), param);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    private HandlerMapping getHandlerMapping(String url) {
        for (HandlerMapping handlerMapping : handlerMappings) {
            if (handlerMapping.getUrl().matcher(url).matches()) {
                return handlerMapping;
            }
        }
        return null;
    }

    /**
     * 映射关系
     */
    public class HandlerMapping {
        private Pattern url;
        private Method method;
        private Object controller;
        private Map<String, Integer> paramIndex = Maps.newHashMap();

        public HandlerMapping(Pattern url, Method method, Object controller) {
            this.url = url;
            this.method = method;
            this.controller = controller;
            initParamIndex();
        }

        private void initParamIndex() {
            Annotation[][] annotations = this.method.getParameterAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                Annotation[] annotation = annotations[i];
                for (int j = 0; j < annotation.length; j++) {
                    if (annotation[j].annotationType()== LeeRequestParam.class) {
                        this.paramIndex.put(((LeeRequestParam) annotation[j]).value(), i);
                    }
                }
            }
            Class<?>[] parameterTypes = this.method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] == HttpServletRequest.class || parameterTypes[i] == HttpServletResponse.class) {
                    this.paramIndex.put(parameterTypes[i].getName(), i);
                }
            }
//                Class parameter = parameters[i].getType();
//                if (parameter.isAnnotationPresent(LeeRequestParam.class)) {
//                    this.paramIndex.put(((LeeRequestParam) parameter.getAnnotation(LeeRequestParam.class)).value(), i);
//                } else if (parameter == HttpServletRequest.class || parameter == HttpServletResponse.class) {
//                    this.paramIndex.put(parameter.getName(), i);
//                }

        }


        public Map<String, Integer> getParamIndex() {
            return paramIndex;
        }

        public Pattern getUrl() {
            return url;
        }

        public void setUrl(Pattern url) {
            this.url = url;
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        public Object getController() {
            return controller;
        }

        public void setController(Object controller) {
            this.controller = controller;
        }
    }


}
