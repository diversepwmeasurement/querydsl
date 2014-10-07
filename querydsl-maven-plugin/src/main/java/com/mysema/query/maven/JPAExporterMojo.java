/*
 * Copyright 2012, Mysema Ltd
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
package com.mysema.query.maven;

import com.mysema.codegen.model.TypeCategory;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import com.mysema.query.codegen.GenericExporter;
import com.mysema.query.codegen.TypeFactory;
import java.lang.annotation.Annotation;
import javax.persistence.Temporal;

/**
 * JPAExporterMojo calls the GenericExporter tool using the classpath of the module
 * 
 * @goal jpa-export
 * @requiresDependencyResolution test
 * @author tiwe
 */
public class JPAExporterMojo extends AbstractExporterMojo {

    @Override
    protected void configure(GenericExporter exporter) {
        super.configure(exporter);
        exporter.setEmbeddableAnnotation(Embeddable.class);
        exporter.setEmbeddedAnnotation(Embedded.class);
        exporter.setEntityAnnotation(Entity.class);
        exporter.setSkipAnnotation(Transient.class);
        exporter.setSupertypeAnnotation(MappedSuperclass.class);
        
        exporter.addAnnotationHelper(new TypeFactory.AnnotationHelper() {

            @Override
            public boolean isSupported(Class<? extends Annotation> annotationClass) {
                return Temporal.class.isAssignableFrom(annotationClass);
            }

            @Override
            public Object getCustomKey(Annotation annotation) {
                return ((Temporal)annotation).value();
            }

            @Override
            public TypeCategory getTypeByAnnotation(Class<?> cl, Annotation annotation) {
                switch (((Temporal)annotation).value()){
                    case DATE:
                        return TypeCategory.DATE;
                    case TIME:
                        return TypeCategory.TIME;
                    case TIMESTAMP:
                        return TypeCategory.DATETIME;
                }
                return null;
            }
        });
    }

}
