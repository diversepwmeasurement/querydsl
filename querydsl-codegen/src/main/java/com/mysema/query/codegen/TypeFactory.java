/*
 * Copyright 2011, Mysema Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.codegen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.mysema.codegen.model.ClassType;
import com.mysema.codegen.model.SimpleType;
import com.mysema.codegen.model.Type;
import com.mysema.codegen.model.TypeCategory;
import com.mysema.codegen.model.TypeExtends;
import com.mysema.codegen.model.TypeSuper;
import com.mysema.codegen.model.Types;
import com.mysema.util.ReflectionUtils;
import java.lang.reflect.AnnotatedElement;

/**
 * TypeFactory is a factory class for {@link Type} instances
 *
 * @author tiwe
 *
 */
public final class TypeFactory {

    private static final Type ANY = new TypeExtends(Types.OBJECT);

    private final Map<TypeKey, Type> cache = new HashMap<TypeKey, Type>();

    private final List<Class<? extends Annotation>> entityAnnotations;
    
    private final List<AnnotationHelper> annotationHelpers = Lists.<AnnotationHelper> newArrayList();

    private final Set<Class<?>> embeddableTypes = new HashSet<Class<?>>();

    private boolean unknownAsEntity = false;

    public TypeFactory() {
        this(Lists.<Class<? extends Annotation>>newArrayList());
    }

    public TypeFactory(List<Class<? extends Annotation>> entityAnnotations) {
        this.entityAnnotations = entityAnnotations;
    }

    public EntityType getEntityType(Class<?> cl) {
        java.lang.reflect.Type generic = cl;
        if (cl.getTypeParameters().length > 0) {
            generic = new ParameterizedTypeImpl(cl, cl.getTypeParameters());
        }
        return (EntityType) get(true, cl, null, generic);
    }

    public Type get(Class<?> cl) {
        return get(cl, cl);
    }
    
    public Type get(Class<?> cl, java.lang.reflect.Type genericType) {
        return get(isEntityClass(cl), cl, null, genericType);
    }

    public Type get(Class<?> cl, AnnotatedElement annotated, java.lang.reflect.Type genericType) {
        return get(isEntityClass(cl), cl, annotated, genericType);
    }
    
    public Type get(boolean entity, Class<?> cl, java.lang.reflect.Type genericType) {
        return get(entity, cl, null, genericType);
    }

    public Type get(boolean entity, Class<?> cl, AnnotatedElement annotated, java.lang.reflect.Type genericType) {
        TypeKey key = new TypeKey(cl, genericType);
        AnnotationHelper annotationHelper = null;
        Annotation selectedAnnotation = null;
        if (annotated != null){
            for (Annotation annotation : annotated.getDeclaredAnnotations()){
                for (AnnotationHelper helper : annotationHelpers) {
                    if (helper.isSupported(annotation.annotationType())){
                        key.annotationClass = annotation.annotationType();
                        selectedAnnotation = annotated.getAnnotation(key.annotationClass);
                        annotationHelper = helper;
                        key.custom = helper.getCustomKey(selectedAnnotation);
                        break;
                    }
                }
            }
        }
        if (cache.containsKey(key)) {
            Type value = cache.get(key);
            if (entity && !(value instanceof EntityType)) {
                value = new EntityType(value);
                cache.put(key, value);
            }
            return value;

        } else {
            Type value = create(entity, cl, annotationHelper, selectedAnnotation, genericType, key);
            cache.put(key, value);
            return value;
        }
    }

    private Type create(boolean entity, Class<?> cl, AnnotationHelper annotationHelper, Annotation annotation, java.lang.reflect.Type genericType,
            TypeKey key) {
        if (cl.isPrimitive()) {
            cl = Primitives.wrap(cl);
        }
        Type value;
        Type[] tempParams = (Type[]) Array.newInstance(Type.class,
                ReflectionUtils.getTypeParameterCount(genericType));
        cache.put(key, new ClassType(cl, tempParams));
        Type[] parameters = getParameters(cl, genericType);

        if (cl.isArray()) {
            Type componentType = get(cl.getComponentType());
            if (cl.getComponentType().isPrimitive()) {
                componentType = Types.PRIMITIVES.get(componentType);
            }
            value = componentType.asArrayType();
        } else if (cl.isEnum()) {
            value = new ClassType(TypeCategory.ENUM, cl);
        } else if (Number.class.isAssignableFrom(cl) && Comparable.class.isAssignableFrom(cl)) {
            value = new ClassType(TypeCategory.NUMERIC, cl, parameters);
        } else if (entity) {
            value = createOther(cl, entity, annotationHelper, annotation, parameters);
        } else if (Map.class.isAssignableFrom(cl)) {
            value = new SimpleType(Types.MAP, parameters[0], asGeneric(parameters[1]));
        } else if (List.class.isAssignableFrom(cl)) {
            value = new SimpleType(Types.LIST, asGeneric(parameters[0]));
        } else if (Set.class.isAssignableFrom(cl)) {
            value = new SimpleType(Types.SET, asGeneric(parameters[0]));
        } else if (Collection.class.isAssignableFrom(cl)) {
            value = new SimpleType(Types.COLLECTION, asGeneric(parameters[0]));
        } else {
            value = createOther(cl, entity, annotationHelper, annotation, parameters);
        }

        if (genericType instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable)genericType;
            if (tv.getBounds().length == 1 && tv.getBounds()[0].equals(Object.class)) {
                value = new TypeSuper(tv.getName(), value);
            } else {
                value = new TypeExtends(tv.getName(), value);
            }
        }

        if (entity && !(value instanceof EntityType)) {
            value = new EntityType(value);
        }
        return value;
    }

    private Type asGeneric(Type type) {
        if (type.getParameters().size() == 0) {
            int count = type.getJavaClass().getTypeParameters().length;
            if (count > 0) {
                return new SimpleType(type, new Type[count]);
            }
        }
        return type;
    }

    private Type createOther(Class<?> cl, boolean entity, AnnotationHelper annotationHelper, Annotation annotation, Type[] parameters) {
        TypeCategory typeCategory = TypeCategory.get(cl.getName());
        if (annotationHelper != null){
            typeCategory = annotationHelper.getTypeByAnnotation(cl, annotation);
        } else if (!typeCategory.isSubCategoryOf(TypeCategory.COMPARABLE) && Comparable.class.isAssignableFrom(cl)
            && !cl.equals(Comparable.class)) {
            typeCategory = TypeCategory.COMPARABLE;
        } else if (embeddableTypes.contains(cl)) {
            typeCategory = TypeCategory.CUSTOM;
        } else if (typeCategory == TypeCategory.SIMPLE && entity) {
            typeCategory = TypeCategory.ENTITY;
        } else if (unknownAsEntity && typeCategory == TypeCategory.SIMPLE && !cl.getName().startsWith("java")) {
            typeCategory = TypeCategory.CUSTOM;
        }
        
        return new ClassType(typeCategory, cl, parameters);
    }

    private Type[] getParameters(Class<?> cl, java.lang.reflect.Type genericType) {
        int parameterCount = ReflectionUtils.getTypeParameterCount(genericType);
        if (parameterCount > 0) {
            return getGenericParameters(cl, genericType, parameterCount);
        } else if (Map.class.isAssignableFrom(cl)) {
            return new Type[]{ Types.OBJECT, Types.OBJECT };
        } else if (Collection.class.isAssignableFrom(cl)) {
            return new Type[]{ Types.OBJECT };
        } else {
            return new Type[0];
        }
    }

    private Type[] getGenericParameters(Class<?> cl, java.lang.reflect.Type genericType,
            int parameterCount) {
        Type[] types = new Type[parameterCount];
        for (int i = 0; i < types.length; i++) {
            types[i] = getGenericParameter(cl, genericType, i);
        }
        return types;
    }

    @SuppressWarnings("rawtypes")
    private Type getGenericParameter(Class<?> cl, java.lang.reflect.Type genericType, int i) {
        java.lang.reflect.Type parameter = ReflectionUtils.getTypeParameter(genericType, i);
        if (parameter instanceof TypeVariable) {
            TypeVariable variable = (TypeVariable)parameter;
            Type rv = get(ReflectionUtils.getTypeParameterAsClass(genericType, i), null, parameter);
            return new TypeExtends(variable.getName(), rv);
        } else if (parameter instanceof WildcardType
            && ((WildcardType)parameter).getUpperBounds()[0].equals(Object.class)
            && ((WildcardType)parameter).getLowerBounds().length == 0) {
            return ANY;
        } else {
            Type rv = get(ReflectionUtils.getTypeParameterAsClass(genericType, i), null, parameter);
            if (parameter instanceof WildcardType) {
                rv = new TypeExtends(rv);
            }
            return rv;
        }
    }

    private boolean isEntityClass(Class<?> cl) {
        for (Class<? extends Annotation> clazz : entityAnnotations) {
            if (cl.getAnnotation(clazz) != null) {
                return true;
            }
        }
        return embeddableTypes.contains(cl);
    }

    public void extendTypes() {
        for (Map.Entry<TypeKey, Type> entry : cache.entrySet()) {
            if (entry.getValue() instanceof EntityType) {
                EntityType entityType = (EntityType)entry.getValue();
                if (entityType.getProperties().isEmpty()) {
                    for (Type type : cache.values()) {
                        if (type.getFullName().equals(entityType.getFullName()) && type instanceof EntityType) {
                            EntityType base = (EntityType)type;
                            for (Property property : base.getProperties()) {
                                entityType.addProperty(property);
                            }
                        }
                    }
                }
            }
        }
    }

    public void setUnknownAsEntity(boolean unknownAsEntity) {
        this.unknownAsEntity = unknownAsEntity;
    }

    public void addEmbeddableType(Class<?> cl) {
        embeddableTypes.add(cl);
    }
    
    public void addAnnotationHelper(AnnotationHelper annotationHelper){
        annotationHelpers.add(annotationHelper);
    }
    
    private static final class TypeKey {
        
        private Class<?> typeClass;
        private java.lang.reflect.Type genericType;
        private Class<? extends Annotation> annotationClass;
        private Object custom;

        private TypeKey(Class<?> cl, java.lang.reflect.Type genericType) {
            this.typeClass = cl;
            this.genericType = genericType;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 17 * hash + (this.typeClass != null ? this.typeClass.hashCode() : 0);
            hash = 17 * hash + (this.genericType != null ? this.genericType.hashCode() : 0);
            hash = 17 * hash + (this.annotationClass != null ? this.annotationClass.hashCode() : 0);
            hash = 17 * hash + (this.custom != null ? this.custom.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TypeKey other = (TypeKey) obj;
            if (this.typeClass != other.typeClass && (this.typeClass == null || !this.typeClass.equals(other.typeClass))) {
                return false;
            }
            if (this.genericType != other.genericType && (this.genericType == null || !this.genericType.equals(other.genericType))) {
                return false;
            }
            if (this.annotationClass != other.annotationClass && (this.annotationClass == null || !this.annotationClass.equals(other.annotationClass))) {
                return false;
            }
            if ((this.custom == null) ? (other.custom != null) : !this.custom.equals(other.custom)) {
                return false;
            }
            return true;
        }
    }

    public static interface AnnotationHelper {
        
        boolean isSupported(Class<? extends Annotation> annotationClass);
        
        Object getCustomKey(Annotation annotation);

        public TypeCategory getTypeByAnnotation(Class<?> cl, Annotation annotation);
    }
}
