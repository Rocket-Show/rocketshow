package com.ascargon.rocketshow;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlTransient;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills a model like a unit using every feature: every property gets a value, every list one element of each type
 * it can hold, booleans and enums a value other than their default. Properties added later are filled as well, so
 * the sample of each release contains everything that release can save.
 */
class SampleFiller {

    // Deep enough for every model, but stops endless nesting
    private final static int MAX_DEPTH = 8;

    // A list can be reachable through two properties (e.g. a getter for the XML name). Fill it only once.
    private final Set<Object> filledCollections = Collections.newSetFromMap(new IdentityHashMap<>());

    private int counter = 0;

    <T> T fill(T object) throws Exception {
        fillProperties(object, 0);
        return object;
    }

    private void fillProperties(Object object, int depth) throws Exception {
        for (PropertyDescriptor property : Introspector.getBeanInfo(object.getClass(), Object.class).getPropertyDescriptors()) {
            Method getter = property.getReadMethod();
            Method setter = property.getWriteMethod();

            if (getter == null || isTransient(object.getClass(), property)) {
                continue;
            }

            // Computed values (e.g. a MIDI message from its parts) can't be filled
            boolean collection = List.class.isAssignableFrom(property.getPropertyType()) || Map.class.isAssignableFrom(property.getPropertyType());
            if (setter == null && !collection) {
                continue;
            }

            Object current = getter.invoke(object);

            if (List.class.isAssignableFrom(property.getPropertyType())) {
                fillList(object, property.getName(), getter, setter, current, depth);
            } else if (Map.class.isAssignableFrom(property.getPropertyType())) {
                fillMap(object, getter, setter, current, property.getName());
            } else if (setter != null) {
                Object value = createValue(property.getPropertyType(), current, property.getName(), depth);
                if (value != null) {
                    setter.invoke(object, value);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void fillList(Object object, String name, Method getter, Method setter, Object current, int depth) throws Exception {
        List<Object> list = current == null ? new ArrayList<>() : (List<Object>) current;

        if (!filledCollections.add(list)) {
            return;
        }

        for (Class<?> elementType : getElementTypes(getter)) {
            Object value = createValue(elementType, null, name, depth);
            if (value != null) {
                list.add(value);
            }
        }

        if (current == null && setter != null) {
            setter.invoke(object, list);
        }
    }

    @SuppressWarnings("unchecked")
    private void fillMap(Object object, Method getter, Method setter, Object current, String name) throws Exception {
        Map<Object, Object> map = current == null ? new HashMap<>() : (Map<Object, Object>) current;

        if (!filledCollections.add(map)) {
            return;
        }

        map.put(name + "-key-" + ++counter, name + "-value-" + ++counter);

        if (current == null && setter != null) {
            setter.invoke(object, map);
        }
    }

    // The types a list can hold: one of each type listed for the XML, or else its element type
    private List<Class<?>> getElementTypes(Method getter) {
        List<Class<?>> types = new ArrayList<>();

        XmlElements xmlElements = getter.getAnnotation(XmlElements.class);
        if (xmlElements != null) {
            for (XmlElement xmlElement : xmlElements.value()) {
                types.add(xmlElement.type());
            }
            return types;
        }

        Type type = getter.getGenericReturnType();
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            types.add(elementType);
        }

        return types;
    }

    private boolean isTransient(Class<?> type, PropertyDescriptor property) {
        if (property.getReadMethod().isAnnotationPresent(XmlTransient.class)) {
            return true;
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(property.getName());
                return field.isAnnotationPresent(XmlTransient.class) || Modifier.isTransient(field.getModifiers());
            } catch (NoSuchFieldException e) {
                // Look in the superclass
            }
        }

        return false;
    }

    private Object createValue(Class<?> type, Object current, String name, int depth) throws Exception {
        if (type == String.class) {
            return name + "-" + ++counter;
        } else if (type == int.class || type == Integer.class) {
            return ++counter;
        } else if (type == long.class || type == Long.class) {
            return (long) ++counter;
        } else if (type == float.class || type == Float.class) {
            return ++counter + 0.5f;
        } else if (type == double.class || type == Double.class) {
            return ++counter + 0.5;
        } else if (type == boolean.class || type == Boolean.class) {
            return current == null || !(Boolean) current;
        } else if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            int index = current == null ? 0 : (((Enum<?>) current).ordinal() + 1) % constants.length;
            return constants[index];
        } else if (type == Date.class) {
            return new Date(1767225600000L + ++counter * 1000L);
        } else if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || !type.getName().startsWith("com.ascargon.")) {
            return null;
        }

        if (depth >= MAX_DEPTH) {
            return null;
        }

        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object value = constructor.newInstance();
        fillProperties(value, depth + 1);
        return value;
    }

}
