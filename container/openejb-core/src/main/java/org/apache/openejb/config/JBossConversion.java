/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.openejb.config;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.JAXBElement;

import org.apache.openejb.jee.ActivationConfig;
import org.apache.openejb.jee.ActivationConfigProperty;
import org.apache.openejb.jee.EjbRef;
import org.apache.openejb.jee.EnterpriseBean;
import org.apache.openejb.jee.EntityBean;
import org.apache.openejb.jee.MessageDrivenBean;
import org.apache.openejb.jee.SessionBean;
import org.apache.openejb.jee.jboss.EjbLocalRef;
import org.apache.openejb.jee.jboss.JaxbJboss;
import org.apache.openejb.jee.jboss.Jboss;
import org.apache.openejb.jee.jboss.JndiName;
import org.apache.openejb.jee.jboss.MessageDriven;
import org.apache.openejb.jee.jboss.Session;
import org.apache.openejb.jee.jboss.cmp.CmpField;
import org.apache.openejb.jee.jboss.cmp.JbosscmpJdbc;
import org.apache.openejb.jee.jpa.AttributeOverride;
import org.apache.openejb.jee.jpa.Attributes;
import org.apache.openejb.jee.jpa.Basic;
import org.apache.openejb.jee.jpa.Column;
import org.apache.openejb.jee.jpa.Entity;
import org.apache.openejb.jee.jpa.EntityMappings;
import org.apache.openejb.jee.jpa.Field;
import org.apache.openejb.jee.jpa.Id;
import org.apache.openejb.jee.jpa.RelationField;
import org.apache.openejb.jee.jpa.Table;
import org.apache.openejb.jee.oejb2.JaxbOpenejbJar2;
import org.apache.openejb.jee.oejb3.EjbDeployment;
import org.apache.openejb.loader.IO;
import org.slf4j.LoggerFactory;
import org.testng.collections.Lists;

public class JBossConversion implements DynamicDeployer {

    @Override
    public final AppModule deploy(final AppModule appModule) {
        for (final EjbModule ejbModule : appModule.getEjbModules()) {

        	final Jboss jboss = openDescriptor("jboss.xml", Jboss.class, ejbModule);
            if (jboss != null) {
                loadJbossDescriptor(ejbModule, jboss);
            }
            
            final JbosscmpJdbc jbosscmpJdbc = openDescriptor("jbosscmp-jdbc.xml", JbosscmpJdbc.class, ejbModule);
            if (jbosscmpJdbc != null) {
                loadJbosscmpJdbcDescriptor(
                		ejbModule.getModuleId(), 
                		appModule.getCmpMappings(), 
                		jbosscmpJdbc);
            }
        }
        return appModule;
    }

	@SuppressWarnings("rawtypes")
	private <T> T openDescriptor(final String desciptorName, final Class<T> clazz, final EjbModule ejbModule) {
        Object altDD = ejbModule.getAltDDs().get(desciptorName);
        if (altDD instanceof String) {
            try {
                altDD = JaxbJboss.unmarshal(clazz, new ByteArrayInputStream(((String) altDD).getBytes()), false);
            } catch (final Exception e) {
                // todo warn about not being able to parse descriptor
            }
        }
        if (altDD instanceof URL) {
            try {
                altDD = JaxbJboss.unmarshal(clazz, IO.read((URL) altDD), false);
            } catch (final Exception e) {
            	System.out.println(e);
                // todo warn about not being able to parse descriptor
            }
        }
        if (altDD instanceof JAXBElement) {
            altDD = ((JAXBElement) altDD).getValue();
        }
        if (clazz.isInstance(altDD)) {
            return clazz.cast(altDD);
        }
        return null;
    }

    private void loadJbossDescriptor(EjbModule ejbModule, Jboss jbossDescriptor) {
        final Map<String, EjbDeployment> deployments = ejbModule.getOpenejbJar().getDeploymentsByEjbName();
        final Map<String, Session> jbossSessions = toMap(jbossDescriptor.getEnterpriseBeans().getSessionOrEntityOrMessageDriven(), Session.class); 
        final Map<String, org.apache.openejb.jee.jboss.Entity> jbossEntities = toMap(jbossDescriptor.getEnterpriseBeans().getSessionOrEntityOrMessageDriven(), org.apache.openejb.jee.jboss.Entity.class); 
        final Map<String, MessageDriven> jbossMessages = toMap(jbossDescriptor.getEnterpriseBeans().getSessionOrEntityOrMessageDriven(), MessageDriven.class); 
        
        for (final EnterpriseBean enterpriseBean : ejbModule.getEjbJar().getEnterpriseBeansByEjbName().values()) {
        	if (enterpriseBean instanceof SessionBean) {
        		
        		//
        		// Session Bean
        		//
        		
        		SessionBean bean = (SessionBean) enterpriseBean;
                final EjbDeployment deployment = deployments.get(bean.getEjbName());
        		final Session jboss = jbossSessions.get(bean.getEjbName());

        		setJndiName(jboss.getJndiName(), "RemoteHome", deployment);
        		setJndiName(jboss.getLocalJndiName(), "LocalHome", deployment);
        		
	            for (final org.apache.openejb.jee.jboss.EjbRef jbossEjbRef : jboss.getEjbRef()) {
	            	setEjbRef(
            			jbossEjbRef.getEjbRefName(), 
            			jbossEjbRef.getJndiName().getvalue(), 
            			deployment, 
            			bean.getEjbLocalRefMap().get(jbossEjbRef.getEjbRefName()));
	            }
	            for (final EjbLocalRef jbossEjbLocalRef : jboss.getEjbLocalRef()) {
	            	setEjbRef(
            			jbossEjbLocalRef.getEjbRefName(), 
            			jbossEjbLocalRef.getLocalJndiName(), 
            			deployment, 
            			bean.getEjbLocalRefMap().get(jbossEjbLocalRef.getEjbRefName()));
	            }
        	} else if  (enterpriseBean instanceof EntityBean) {
        		
        		//
        		// Entity Bean
        		//
        		
        		EntityBean bean = (EntityBean) enterpriseBean;
                final EjbDeployment deployment = deployments.get(bean.getEjbName());
        		final org.apache.openejb.jee.jboss.Entity jboss = jbossEntities.get(bean.getEjbName());

        		setJndiName(jboss == null ? null : jboss.getJndiName(), "RemoteHome", deployment);
        		setJndiName(jboss == null ? null : jboss.getLocalJndiName(), "LocalHome", deployment);
        		
	            if (jboss != null) {
		            for (final org.apache.openejb.jee.jboss.EjbRef jbossEjbRef : jboss.getEjbRef()) {
		            	setEjbRef(
	            			jbossEjbRef.getEjbRefName(), 
	            			jbossEjbRef.getJndiName().getvalue(), 
	            			deployment, 
	            			bean.getEjbLocalRefMap().get(jbossEjbRef.getEjbRefName()));
		            }
		            for (final EjbLocalRef jbossEjbLocalRef : jboss.getEjbLocalRef()) {
		            	setEjbRef(
	            			jbossEjbLocalRef.getEjbRefName(), 
	            			jbossEjbLocalRef.getLocalJndiName(), 
	            			deployment, 
	            			bean.getEjbLocalRefMap().get(jbossEjbLocalRef.getEjbRefName()));
		            }
	            }
        	} else if  (enterpriseBean instanceof MessageDrivenBean) {
        		
        		//
        		// MessageDriven Bean
        		//
        		
        		MessageDrivenBean bean = (MessageDrivenBean) enterpriseBean;
                final EjbDeployment deployment = deployments.get(bean.getEjbName());
        		final MessageDriven jboss = jbossMessages.get(bean.getEjbName());

        		setJndiName(jboss == null ? null : jboss.getLocalJndiName(), "LocalHome", deployment);
        		
	            if (jboss != null) {
		            for (final org.apache.openejb.jee.jboss.EjbRef jbossEjbRef : jboss.getEjbRef()) {
		            	setEjbRef(
	            			jbossEjbRef.getEjbRefName(), 
	            			jbossEjbRef.getJndiName().getvalue(), 
	            			deployment, 
	            			bean.getEjbLocalRefMap().get(jbossEjbRef.getEjbRefName()));
		            }
		            for (final EjbLocalRef jbossEjbLocalRef : jboss.getEjbLocalRef()) {
		            	setEjbRef(
	            			jbossEjbLocalRef.getEjbRefName(), 
	            			jbossEjbLocalRef.getLocalJndiName(), 
	            			deployment, 
	            			bean.getEjbLocalRefMap().get(jbossEjbLocalRef.getEjbRefName()));
		            }
		            
		            final org.apache.openejb.jee.jboss.ActivationConfig activationConfigType = jboss.getActivationConfig();
		            if (activationConfigType != null) {
		                ActivationConfig activationConfig = bean.getActivationConfig();
		                if (activationConfig == null) {
		                    activationConfig = new ActivationConfig();
		                    bean.setActivationConfig(activationConfig);
		                }
		                for (final org.apache.openejb.jee.jboss.ActivationConfigProperty propertyType : activationConfigType.getActivationConfigProperty()) {
		                    final ActivationConfigProperty property = new ActivationConfigProperty(
		                        propertyType.getActivationConfigPropertyName(),
		                        propertyType.getActivationConfigPropertyValue());
		                    activationConfig.getActivationConfigProperty().add(property);
		                }
		            }

	            }
        	} 
        }
    }
    
    private void setEjbRef(
    		String refName,
    		String jndiName,
    		EjbDeployment deployment, 
    		Object ejbRef) {

		if (refName == null || jndiName == null || ejbRef == null) {
			return;
		}
	
        if (deployment.getEjbLink(refName) != null) { 
        	// don't overwrite refs that have been already set
            return;
        }

        if (ejbRef instanceof EjbRef) {
            ((EjbRef)ejbRef).setMappedName(jndiName);
        } else if (ejbRef instanceof EjbLocalRef) {
            ((org.apache.openejb.jee.EjbLocalRef)ejbRef).setMappedName(jndiName);
        }
    }

    private void setJndiName(Object jndi, String iface, EjbDeployment deployment) {
        if (jndi != null) {
        	if (jndi instanceof JndiName) {
        		JndiName jndiName = (JndiName) jndi;
        		deployment.getJndi().add(new org.apache.openejb.jee.oejb3.Jndi(jndiName.getvalue(), iface));
        	} else { 
        		deployment.getJndi().add(new org.apache.openejb.jee.oejb3.Jndi(jndi.toString(), iface));
        	}
        } else {
        	deployment.getJndi().add(new org.apache.openejb.jee.oejb3.Jndi(deployment.getEjbName(), iface));
        }
    }
    
    private final void loadJbosscmpJdbcDescriptor(final String moduleId, final EntityMappings entityMappings, JbosscmpJdbc jbosscmpJdbc) {
        final Map<String, EntityData> entities = new TreeMap<String, EntityData>();
        if (entityMappings != null) {
            for (final Entity entity : entityMappings.getEntity()) {
                try {
                    entities.put(entity.getDescription(), new EntityData(entity));
                } catch (final IllegalArgumentException e) {
                    LoggerFactory.getLogger(this.getClass()).error(e.getMessage(), e);
                }
            }
        }
        for (final org.apache.openejb.jee.jboss.cmp.Entity bean : jbosscmpJdbc.getEnterpriseBeans().getEntity()) {
            final EntityData entityData = entities.get(moduleId + "#" + bean.getEjbName().getContent());
            if (entityData == null) {
                // todo warn no such ejb in the ejb-jar.xml
                continue;
            }
            
            final Table table = new Table();
            table.setName(bean.getTableName().getContent());
            entityData.entity.setTable(table);

//            bean.getRowLocking();
//            bean.getReadAhead();
//            bean.getReadOnly();
//            bean.getReadTimeOut();
            
            for (final CmpField cmpField : bean.getCmpField()) {
                final String cmpFieldName = cmpField.getFieldName().getContent();
                final Field field = entityData.fields.get(cmpFieldName);
                if (field == null) {
                    // todo warn no such cmp-field in the ejb-jar.xml
                    continue;
                }
                final Column column = new Column();
                column.setName(cmpField.getColumnName().getContent());
                column.setColumnDefinition(cmpField.getSqlType().getContent());
                field.setColumn(column);
            }

//            for (final Query query : bean.getQuery()) {
//                final NamedQuery namedQuery = new NamedQuery();
//                final QueryMethod queryMethod = query.getQueryMethod();
//
//                // todo deployment id could change in one of the later conversions... use entity name instead, but we need to save it off
//                final StringBuilder name = new StringBuilder();
//                name.append(entityData.entity.getName()).append(".").append(queryMethod.getMethodName());
//                if (queryMethod.getMethodParams() != null && !queryMethod.getMethodParams().getMethodParam().isEmpty()) {
//                    name.append('(');
//                    boolean first = true;
//                    for (final MethodParam methodParam : queryMethod.getMethodParams().getMethodParam()) {
//                        if (!first) {
//                            name.append(",");
//                        }
//                        name.append(methodParam);
//                        first = false;
//                    }
//                    name.append(')');
//                }
//                namedQuery.setName(name.toString());
//
//                namedQuery.setQuery(query.getJbossQl().getContent());
//                entityData.entity.getNamedQuery().add(namedQuery);
//            }
        }
    }

    private <T> List<T> filter(List<?> list, Class<T> clazz) {
    	List<T> result = Lists.newArrayList();
    	for (Object o : list) {
			if (clazz.isInstance(o)) {
				result.add((T) o);
			}
		}
    	return result;
    }
    
    @SuppressWarnings("unchecked")
	private <T> Map<String, T> toMap(List<?> list, Class<T> clazz) {
    	Map<String, T> result = new HashMap<>();
    	for (Object o : list) {
    		String ejbName = null;
    		if (o instanceof Session) {
    			Session bean = (Session) o;
    			ejbName = bean.getEjbName();
    		} else if (o instanceof Entity) {
    			Entity bean = (Entity) o;
    			ejbName = bean.getEjbName();
    		} else if (o instanceof MessageDriven) {
    			MessageDriven bean = (MessageDriven) o;
    			ejbName = bean.getEjbName();
    		} else {
    			continue;
    		}
    			
			if (clazz.isInstance(o)) {
				result.put(ejbName, (T) o);
			}
		}
    	return result;
    }
    
    private static class EntityData {

        private final Entity entity;
        private final Map<String, Field> fields = new TreeMap<String, Field>();
        private final Map<String, RelationField> relations = new TreeMap<String, RelationField>();

        public EntityData(final Entity e) {

            this.entity = e;

            if (this.entity == null) {
                throw new IllegalArgumentException("entity is null");
            }

            final Attributes attributes = this.entity.getAttributes();

            if (attributes != null) {
                for (final Id id : attributes.getId()) {
                    this.fields.put(id.getName(), id);
                }

                for (final Basic basic : attributes.getBasic()) {
                    this.fields.put(basic.getName(), basic);
                }

                for (final RelationField relationField : attributes.getOneToOne()) {
                    this.relations.put(relationField.getName(), relationField);
                }

                for (final RelationField relationField : attributes.getOneToMany()) {
                    this.relations.put(relationField.getName(), relationField);
                }

                for (final RelationField relationField : attributes.getManyToOne()) {
                    this.relations.put(relationField.getName(), relationField);
                }

                for (final RelationField relationField : attributes.getManyToMany()) {
                    this.relations.put(relationField.getName(), relationField);
                }
            }

            for (final AttributeOverride attributeOverride : this.entity.getAttributeOverride()) {
                this.fields.put(attributeOverride.getName(), attributeOverride);
            }
        }
    }
}
