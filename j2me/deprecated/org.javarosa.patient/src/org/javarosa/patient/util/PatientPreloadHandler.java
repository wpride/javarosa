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

package org.javarosa.patient.util;

import java.util.Date;
import java.util.Vector;

import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.model.utils.IPreloadHandler;
import org.javarosa.patient.model.Patient;
import org.javarosa.patient.model.data.ImmunizationAnswerData;
import org.javarosa.patient.model.data.ImmunizationData;
import org.javarosa.patient.model.data.NumericListData;

public class PatientPreloadHandler implements IPreloadHandler {
	
	/** The patient for this handler */
	private Patient patient;
	
	/**
	 * Creates a preload handler that can pull values from
	 * the patient object provided.
	 * 
	 * @param thePatient the patient whose data is to be 
	 * retrevied from this preload handler
	 */
	public PatientPreloadHandler(Patient thePatient) {
		patient = thePatient;
	}
	
	/**
	 * 
	 */
	public IAnswerData handlePreload(String preloadParams) {
		System.out.println("Patient preloader! Params: " + preloadParams);
		IAnswerData returnVal = null;
		if("vaccination_table".equals(preloadParams)) {
			ImmunizationData table = patient.getVaccinations();
			int largestAge = 0;
			Date birthDate = patient.getBirthDate();
			int months = DateUtils.getMonthsDifference(birthDate, new Date());
			if(months >= 9) {
				largestAge = 4;
			} else if(months >= 3) {
				largestAge = 3;
			} else if(months >= 2) {
				largestAge = 2;
			} else if(months >= 1){
				largestAge = 1;
			}
			table.setLargestAgeColumn(largestAge);
			returnVal = new ImmunizationAnswerData(table);
		} else if(preloadParams.equals("monthsOnTreatment")) {
			DateData dateData = (DateData)patient.getRecord("treatmentStart");
			if(dateData != null) {
				int months = DateUtils.getMonthsDifference((Date)dateData.getValue(), new Date()); 
				returnVal = new IntegerData(months);
			}
		}
		else {
			int selectorStart = preloadParams.indexOf("[");
			if(selectorStart == -1) {
				returnVal = (IAnswerData)patient.getRecord(preloadParams);
			} else {
				String type = preloadParams.substring(0, selectorStart);
				String selector = preloadParams.substring(selectorStart, preloadParams.length()); 
				Vector data = patient.getRecordSet(type, selector);
				returnVal = new NumericListData();
				returnVal.setValue(data);
			}
		}
		return returnVal;
	}

	public boolean handlePostProcess(TreeElement node, String params) {
		IAnswerData data = node.getValue();

		if ("vaccination_table".equals(params)) {
			patient.setVaccinations((ImmunizationData)((ImmunizationAnswerData)data).getValue());
			return true;
		} else {
			int selectorStart = params.indexOf("[");
			if(selectorStart == -1) {
				//modifying such values not supported right now
			} else {
				String type = params.substring(0, selectorStart);
				NumericListData combinedList = new NumericListData();
				combinedList.setValue(patient.getRecordSet(type, "[0:N]"));
				combinedList.mergeList((NumericListData)data);
				patient.setRecord(type, combinedList);
			}
		}
		
		return false;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#preloadHandled()
	 */
	public String preloadHandled() {
		return "patient";
	}

}
