/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.demo.properties;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.properties.IPropertyRules;

/**
 * Application-specific properties for the JavaRosa reference
 * implementation.
 * 
 * @author Clayton Sims
 *
 */
public class DemoAppProperties implements IPropertyRules {
	    Hashtable rules;
	    Vector readOnlyProperties;

		// http, since it doesn't go in transport layer anymore
	    public final static String POST_URL_PROPERTY = "PostURL";
	    public final static String FORM_URL_PROPERTY = "form_url";
	    
	    /**
	     * Creates the JavaRosa set of property rules
	     */
	    public DemoAppProperties() {
	        rules = new Hashtable();
	        readOnlyProperties = new Vector();
	        	        
	        // PostURL Property
	        rules.put(POST_URL_PROPERTY, new Vector());
	        rules.put(FORM_URL_PROPERTY, new Vector());
	    }

	    /** (non-Javadoc)
	     *  @see org.javarosa.properties.IPropertyRules#allowableValues(String)
	     */
	    public Vector allowableValues(String propertyName) {
	        return (Vector)rules.get(propertyName);
	    }

	    /** (non-Javadoc)
	     *  @see org.javarosa.properties.IPropertyRules#checkValueAllowed(String, String)
	     */
	    public boolean checkValueAllowed(String propertyName, String potentialValue) {
	        Vector prop = ((Vector)rules.get(propertyName));
	        if(prop.size() != 0) {
	            //Check whether this is a dynamic property
	            if(prop.size() == 1 && checkPropertyAllowed((String)prop.elementAt(0))) {
	                // If so, get its list of available values, and see whether the potentival value is acceptable.
	                return ((Vector)PropertyManager._().getProperty((String)prop.elementAt(0))).contains(potentialValue);
	            }
	            else {
	                return ((Vector)rules.get(propertyName)).contains(potentialValue);
	            }
	        }
	        else
	            return true;
	    }

	    /** (non-Javadoc)
	     *  @see org.javarosa.properties.IPropertyRules#allowableProperties()
	     */
	    public Vector allowableProperties() {
	        Vector propList = new Vector();
	        Enumeration iter = rules.keys();
	        while (iter.hasMoreElements()) {
	            propList.addElement(iter.nextElement());
	        }
	        return propList;
	    }

	    /** (non-Javadoc)
	     *  @see org.javarosa.properties.IPropertyRules#checkPropertyAllowed)
	     */
	    public boolean checkPropertyAllowed(String propertyName) {
	        Enumeration iter = rules.keys();
	        while (iter.hasMoreElements()) {
	            if(propertyName.equals(iter.nextElement())) {
	                return true;
	            }
	        }
	        return false;
	    }
	    
	    /** (non-Javadoc)
	     *  @see org.javarosa.properties.IPropertyRules#checkPropertyUserReadOnly)
	     */
	    public boolean checkPropertyUserReadOnly(String propertyName){
	        return readOnlyProperties.contains(propertyName);
	    }
	    /*
	     * (non-Javadoc)
	     * @see org.javarosa.core.services.properties.IPropertyRules#getHumanReadableDescription(java.lang.String)
	     */
	    public String getHumanReadableDescription(String propertyName) {
	    	if(POST_URL_PROPERTY.equals(propertyName)) {
	    		return Localization.get("jrdemo.posturi");
	    	} else if(FORM_URL_PROPERTY.equals(propertyName)) {
	    		return Localization.get("jrdemo.formuri");
	    	}
	    	
	    	return propertyName;
	    }

	    /*
	     * (non-Javadoc)
	     * @see org.javarosa.core.services.properties.IPropertyRules#getHumanReadableValue(java.lang.String, java.lang.String)
	     */
	    public String getHumanReadableValue(String propertyName, String value) {
	    	return value;
	    }
	    /*
	     * (non-Javadoc)
	     * @see org.javarosa.core.services.properties.IPropertyRules#handlePropertyChanges(java.lang.String)
	     */
	    public void handlePropertyChanges(String propertyName) {
	    	//Nothing
	    }
}
