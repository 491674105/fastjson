package com.alibaba.fastjson.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;

public class DeserializeBeanInfo {

    private final Class<?>        clazz;
    private final Type            type;
    private Constructor<?>        defaultConstructor;
    private Constructor<?>        creatorConstructor;
    private Method                factoryMethod;

    private final List<FieldInfo> fieldList = new ArrayList<FieldInfo>();

    public DeserializeBeanInfo(Class<?> clazz){
        super();
        this.clazz = clazz;
        this.type = clazz;
    }

    public Constructor<?> getDefaultConstructor() {
        return defaultConstructor;
    }

    public void setDefaultConstructor(Constructor<?> defaultConstructor) {
        this.defaultConstructor = defaultConstructor;
    }

    public Constructor<?> getCreatorConstructor() {
        return creatorConstructor;
    }

    public void setCreatorConstructor(Constructor<?> createConstructor) {
        this.creatorConstructor = createConstructor;
    }

    public Method getFactoryMethod() {
        return factoryMethod;
    }

    public void setFactoryMethod(Method factoryMethod) {
        this.factoryMethod = factoryMethod;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public Type getType() {
        return type;
    }

    public List<FieldInfo> getFieldList() {
        return fieldList;
    }

    public static DeserializeBeanInfo computeSetters(Class<?> clazz) {
        DeserializeBeanInfo beanInfo = new DeserializeBeanInfo(clazz);

        Constructor<?> defaultConstructor = getDefaultConstructor(clazz);
        if (defaultConstructor != null) {
            defaultConstructor.setAccessible(true);
            beanInfo.setDefaultConstructor(defaultConstructor);
        } else if (defaultConstructor == null && !(clazz.isInterface())) {
            Constructor<?> creatorConstructor = getCreatorConstructor(clazz);
            if (creatorConstructor != null) {
                creatorConstructor.setAccessible(true);
                beanInfo.setCreatorConstructor(creatorConstructor);

                for (int i = 0; i < creatorConstructor.getParameterTypes().length; ++i) {
                    Annotation[] paramAnnotations = creatorConstructor.getParameterAnnotations()[i];
                    JSONField fieldAnnotation = null;
                    for (Annotation paramAnnotation : paramAnnotations) {
                        if (paramAnnotation instanceof JSONField) {
                            fieldAnnotation = (JSONField) paramAnnotation;
                            break;
                        }
                    }
                    if (fieldAnnotation == null) {
                        throw new JSONException("illegal json creator");
                    }

                    Class<?> fieldClass = creatorConstructor.getParameterTypes()[i];
                    Type fieldType = creatorConstructor.getGenericParameterTypes()[i];
                    beanInfo.getFieldList().add(new FieldInfo(fieldAnnotation, clazz, fieldClass, fieldType));
                }
                return beanInfo;
            }

            Method factoryMethod = getFactoryMethod(clazz);
            if (factoryMethod != null) {
                factoryMethod.setAccessible(true);
                beanInfo.setFactoryMethod(factoryMethod);

                for (int i = 0; i < factoryMethod.getParameterTypes().length; ++i) {
                    Annotation[] paramAnnotations = factoryMethod.getParameterAnnotations()[i];
                    JSONField fieldAnnotation = null;
                    for (Annotation paramAnnotation : paramAnnotations) {
                        if (paramAnnotation instanceof JSONField) {
                            fieldAnnotation = (JSONField) paramAnnotation;
                            break;
                        }
                    }
                    if (fieldAnnotation == null) {
                        throw new JSONException("illegal json creator");
                    }

                    Class<?> fieldClass = factoryMethod.getParameterTypes()[i];
                    Type fieldType = factoryMethod.getGenericParameterTypes()[i];
                    beanInfo.getFieldList().add(new FieldInfo(fieldAnnotation, clazz, fieldClass, fieldType));
                }
                return beanInfo;
            }

            throw new JSONException("default constructor not found.");
        }

        for (Method method : clazz.getMethods()) {
            String methodName = method.getName();
            if (methodName.length() < 4) {
                continue;
            }

            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            if (!method.getReturnType().equals(Void.TYPE)) {
                continue;
            }

            if (method.getParameterTypes().length != 1) {
                continue;
            }

            JSONField annotation = method.getAnnotation(JSONField.class);

            if (annotation != null) {
                if (!annotation.deserialize()) {
                    continue;
                }

                if (annotation.name().length() != 0) {
                    String propertyName = annotation.name();
                    beanInfo.getFieldList().add(new FieldInfo(propertyName, method, null));
                    method.setAccessible(true);
                    continue;
                }
            }

            if (methodName.startsWith("set") && Character.isUpperCase(methodName.charAt(3))) {
                String propertyName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);

                Field field = getField(clazz, propertyName);
                if (field != null) {

                    JSONField fieldAnnotation = field.getAnnotation(JSONField.class);

                    if (fieldAnnotation != null && fieldAnnotation.name().length() != 0) {
                        propertyName = fieldAnnotation.name();

                        beanInfo.getFieldList().add(new FieldInfo(propertyName, method, field));
                        continue;
                    }
                }

                beanInfo.getFieldList().add(new FieldInfo(propertyName, method, null));
                method.setAccessible(true);
            }
        }

        return beanInfo;
    }

    public static Field getField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (Exception e) {
            return null;
        }
    }

    public static Constructor<?> getDefaultConstructor(Class<?> clazz) {
        Constructor<?> defaultConstructor = null;
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                defaultConstructor = constructor;
                break;
            }
        }
        return defaultConstructor;
    }

    public static Constructor<?> getCreatorConstructor(Class<?> clazz) {
        Constructor<?> creatorConstructor = null;

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            JSONCreator annotation = constructor.getAnnotation(JSONCreator.class);
            if (annotation != null) {
                if (creatorConstructor != null) {
                    throw new JSONException("multi-json creator");
                }

                creatorConstructor = constructor;
                break;
            }
        }
        return creatorConstructor;
    }

    public static Method getFactoryMethod(Class<?> clazz) {
        Method factoryMethod = null;

        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            if (!clazz.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            JSONCreator annotation = method.getAnnotation(JSONCreator.class);
            if (annotation != null) {
                if (factoryMethod != null) {
                    throw new JSONException("multi-json creator");
                }

                factoryMethod = method;
                break;
            }
        }
        return factoryMethod;
    }

}
