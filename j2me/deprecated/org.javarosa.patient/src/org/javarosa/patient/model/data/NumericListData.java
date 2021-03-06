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

package org.javarosa.patient.model.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.patient.model.IPatientRecord;
import org.javarosa.patient.util.DateValueTuple;
import org.javarosa.patient.util.SelectorParser;

/**
 * @author Clayton Sims
 *
 */
public class NumericListData implements IAnswerData, IPatientRecord {

	/** DateValueTuple */
	Vector valueList = new Vector();
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.model.data.IAnswerData#getDisplayText()
	 */
	public String getDisplayText() {
		return "Data List";
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.data.IAnswerData#getValue()
	 */
	public Object getValue() {
		return valueList;
	}
	

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.data.IAnswerData#setValue(java.lang.Object)
	 */
	public void setValue(Object o) { 
		if(o != null) {
			valueList = (Vector)o;
		}
	}
	
	/**
	 * Gets the latest measurement that has been recorded
	 * 
	 * @return The integer value of the latest recorded measurment. 
	 */
	public int getLatestMeasurement() {
		if(valueList.size() < 1 ) {
			//throw excepton maybe? Doesn't seem like an exceptional case
			//Then again, returning -1 seems explicitly wrong
			return -1;
		}
		else {
			DateValueTuple entry = (DateValueTuple) valueList.elementAt(valueList.size() -1);
			return entry.value;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.javarosa.patient.model.IPatientRecord#getHistoricalRecords(java.lang.String)
	 */
	public Vector getHistoricalRecords(String delimeters) {
		return SelectorParser.selectValues(delimeters, valueList);
	}
	
	/**
	 * Adds the measurement to this record.
	 * 
	 * @param entry The record entry to be added
	 */
	public void addMeasurement(DateValueTuple entry) {
		int i;
		for(i = 0 ; i < valueList.size() ; ++i ) {
			DateValueTuple curEntry = (DateValueTuple) valueList.elementAt(i);
			if(curEntry.date.getTime() > entry.date.getTime() ) {
				break;
			}
		}
		valueList.insertElementAt(entry, i);
	}

	//combine with the values from another NumericListData. result will be the union of both sets
	//if both lists contain a reading for the same date, the value in the list being merged in takes precedence
	public void mergeList (NumericListData nld) {
		if (nld == null || nld.getValue() == null || ((Vector)nld.getValue()).size() == 0)
			return;
		
		Vector newList = (Vector)nld.getValue();
		Vector merged = new Vector();
		int i = 0, j = 0;
		while (i < valueList.size() || j < newList.size()) {
			DateValueTuple a = (i < valueList.size() ? ((DateValueTuple)valueList.elementAt(i)) : null);
			DateValueTuple b = (j < newList.size() ? ((DateValueTuple)newList.elementAt(j)) : null);
			long dateA = (a == null ? Long.MAX_VALUE : a.date.getTime());
			long dateB = (b == null ? Long.MAX_VALUE : b.date.getTime());
			
			if (dateA < dateB) {
				merged.addElement(a);
				i++;
			} else if (dateA > dateB) {
				merged.addElement(b);
				j++;
			} else { //if both lists contain reading for same date, use the newer value
				merged.addElement(b);
				i++;
				j++;
			}
		}

		if (merged.size() > 0)
			setValue(merged);
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.utilities.Externalizable#readExternal(java.io.DataInputStream)
	 */
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		valueList = (Vector)ExtUtil.read(in, new ExtWrapList(DateValueTuple.class));
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.utilities.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	 public void writeExternal(DataOutputStream out) throws IOException {
		 ExtUtil.write(out, new ExtWrapList(valueList));
	}

	public IAnswerData clone() {
		NumericListData data = new NumericListData();
		Enumeration en = valueList.elements();
		while(en.hasMoreElements()) {
			DateValueTuple entry = (DateValueTuple)en.nextElement();
			data.valueList.addElement(entry.clone());
		}
		return data;
	}

	public UncastData uncast() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public IAnswerData cast(UncastData data) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

}
