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

package org.javarosa.xpath.expr;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import me.regexp.RE;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.util.MathUtils;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.IExprDataType;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathNodeset;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.XPathUnhandledException;
import org.javarosa.xpath.XPathUnsupportedException;
import org.javarosa.xpath.parser.XPathSyntaxException;

/**
 * Representation of an xpath function expression.
 * 
 * All of the built-in xpath functions are included here, as well as the xpath type conversion logic
 * 
 * Evaluation of functions can delegate out to custom function handlers that must be registered at
 * runtime.
 * 
 * @author Drew Roos
 *
 */
public class XPathFuncExpr extends XPathExpression {
	public XPathQName id;			//name of the function
	public XPathExpression[] args;	//argument list

	public XPathFuncExpr () { } //for deserialization
	
	public XPathFuncExpr (XPathQName id, XPathExpression[] args) throws XPathSyntaxException{
		
		if(id.name.equals("if")){
			if(args.length != 3){
				throw new XPathSyntaxException("if() function requires 3 arguments but only " + args.length + " are present.");
			}
		}
		
		this.id = id;
		this.args = args;
	}
	
	public String toString () {
		StringBuffer sb = new StringBuffer();
		
		sb.append("{func-expr:");	
		sb.append(id.toString());
		sb.append(",{");
		for (int i = 0; i < args.length; i++) {
			sb.append(args[i].toString());
			if (i < args.length - 1)
				sb.append(",");
		}
		sb.append("}}");
		
		return sb.toString();
	}
	
	public boolean equals (Object o) {
		if (o instanceof XPathFuncExpr) {
			XPathFuncExpr x = (XPathFuncExpr)o;
			
			//Shortcuts for very easily comprable values
			//We also only return "True" for methods we expect to return the same thing. This is not good
			//practice in Java, since o.equals(o) will return false. We should evaluate that differently.
			//Dec 8, 2011 - Added "uuid", since we should never assume one uuid equals another
			//May 6, 2013 - Added "random", since two calls asking for a random 
			if(!id.equals(x.id) || args.length != x.args.length || id.toString().equals("uuid") || id.toString().equals("random")) {
				return false;
			}
			
			return ExtUtil.arrayEquals(args, x.args);
		} else {
			return false;
		}
	}
	
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		id = (XPathQName)ExtUtil.read(in, XPathQName.class);
		Vector v = (Vector)ExtUtil.read(in, new ExtWrapListPoly(), pf);
		
		args = new XPathExpression[v.size()];
		for (int i = 0; i < args.length; i++)
			args[i] = (XPathExpression)v.elementAt(i);		
	}

	public void writeExternal(DataOutputStream out) throws IOException {
		Vector v = new Vector();
		for (int i = 0; i < args.length; i++)
			v.addElement(args[i]);

		ExtUtil.write(out, id);
		ExtUtil.write(out, new ExtWrapListPoly(v));
	}
	
	/**
	 * Evaluate the function call.
	 * 
	 * First check if the function is a member of the built-in function suite. If not, then check
	 * for any custom handlers registered to handler the function. If not, throw and exception.
	 * 
	 * Both function name and appropriate arguments are taken into account when finding a suitable
	 * handler. For built-in functions, the number of arguments must match; for custom functions,
	 * the supplied arguments must match one of the function prototypes defined by the handler.
	 * 
	 */
	public Object eval (DataInstance model, EvaluationContext evalContext) {
		String name = id.toString();
		Object[] argVals = new Object[args.length];
		
		Hashtable funcHandlers = evalContext.getFunctionHandlers();
		
		//TODO: Func handlers should be able to declare the desire for short circuiting as well
		if(name.equals("if") && args.length == 3) {
			return ifThenElse(model, evalContext, args, argVals);	
		} else if (name.equals("coalesce") && args.length == 2) {
			//Not sure if unpacking here is quiiite right, but it seems right
			argVals[0] = XPathFuncExpr.unpack(args[0].eval(model, evalContext));
			if(!isNull(argVals[0])) { return argVals[0]; }
			else {
				argVals[1] = args[1].eval(model, evalContext);
				return argVals[1];
			}
		} 
		
		for (int i = 0; i < args.length; i++) {
			argVals[i] = args[i].eval(model, evalContext);
		}
			
		try {
			
			//check built-in functions
			if (name.equals("true") && args.length == 0) {
				return Boolean.TRUE;
			} else if (name.equals("false") && args.length == 0) {
				return Boolean.FALSE;
			} else if (name.equals("boolean") && args.length == 1) {
				return toBoolean(argVals[0]);
			} else if (name.equals("number") && args.length == 1) {
				return toNumeric(argVals[0]);
			} else if (name.equals("int") && args.length == 1) { //non-standard
				return toInt(argVals[0]);
			} else if (name.equals("double") && args.length == 1) { //non-standard
				return toDouble(argVals[0]);
			} else if (name.equals("string") && args.length == 1) {
				return toString(argVals[0]);			
			} else if (name.equals("date") && args.length == 1) { //non-standard
				return toDate(argVals[0]);				
			} else if (name.equals("not") && args.length == 1) {
				return boolNot(argVals[0]);
			} else if (name.equals("boolean-from-string") && args.length == 1) {
				return boolStr(argVals[0]);
			} else if (name.equals("format-date") && args.length == 2) {
				return dateStr(argVals[0], argVals[1]);
			} else if ((name.equals("selected") || name.equals("is-selected")) && args.length == 2) { //non-standard
				return multiSelected(argVals[0], argVals[1]);
			} else if (name.equals("count-selected") && args.length == 1) { //non-standard
				return countSelected(argVals[0]);		
			} else if (name.equals("selected-at") && args.length == 2) { //non-standard
				return selectedAt(argVals[0], argVals[1]);		
			} else if (name.equals("position") && (args.length == 0 || args.length == 1)) {
				//TODO: Technically, only the 0 length argument is valid here.
				if(args.length == 1) {
					return position(((XPathNodeset)argVals[0]).getRefAt(0));
				} else {
					if(evalContext.getContextPosition() != -1) { 
						return new Double(evalContext.getContextPosition());
					}
					return position(evalContext.getContextRef());
				}
			}  else if (name.equals("count") && args.length == 1) {
				return count(argVals[0]);
			} else if (name.equals("sum") && args.length == 1) {
				if (argVals[0] instanceof XPathNodeset) {
					return sum(((XPathNodeset)argVals[0]).toArgList());
				} else {
					throw new XPathTypeMismatchException("not a nodeset");				
				}
			} else if (name.equals("max")) {
				if (argVals[0] instanceof XPathNodeset) {
					return max(((XPathNodeset)argVals[0]).toArgList());
				} else {
					return max(argVals);				
				}
			}  else if (name.equals("min")) {
				if (argVals[0] instanceof XPathNodeset) {
					return min(((XPathNodeset)argVals[0]).toArgList());
				} else {
					return min(argVals);				
				}
			} else if (name.equals("today") && args.length == 0) {
				return DateUtils.roundDate(new Date());
			} else if (name.equals("now") && args.length == 0) {
				return new Date();
			} else if (name.equals("concat")) {
				if (args.length == 1 && argVals[0] instanceof XPathNodeset) {
					return join("", ((XPathNodeset)argVals[0]).toArgList());
				} else {
					return join("", argVals);
				}
			} else if (name.equals("join") && args.length >= 1) {
				if (args.length == 2 && argVals[1] instanceof XPathNodeset) {
					return join(argVals[0], ((XPathNodeset)argVals[1]).toArgList());
				} else {
					return join(argVals[0], subsetArgList(argVals, 1));
				}
			} else if (name.equals("substr") && (args.length == 2 || args.length == 3)) {
				return substring(argVals[0], argVals[1], args.length == 3 ? argVals[2] : null);
			} else if (name.equals("string-length") && args.length == 1) {
				return stringLength(argVals[0]);
			} else if (name.equals("checklist") && args.length >= 2) { //non-standard
				if (args.length == 3 && argVals[2] instanceof XPathNodeset) {
					return checklist(argVals[0], argVals[1], ((XPathNodeset)argVals[2]).toArgList());
				} else {
					return checklist(argVals[0], argVals[1], subsetArgList(argVals, 2));
				}
			} else if (name.equals("weighted-checklist") && args.length >= 2 && args.length % 2 == 0) { //non-standard
				if (args.length == 4 && argVals[2] instanceof XPathNodeset && argVals[3] instanceof XPathNodeset) {
					Object[] factors = ((XPathNodeset)argVals[2]).toArgList();
					Object[] weights = ((XPathNodeset)argVals[3]).toArgList();
					if (factors.length != weights.length) {
						throw new XPathTypeMismatchException("weighted-checklist: nodesets not same length");
					}
					return checklistWeighted(argVals[0], argVals[1], factors, weights);
				} else {
					return checklistWeighted(argVals[0], argVals[1], subsetArgList(argVals, 2, 2), subsetArgList(argVals, 3, 2));
				}
			} else if (name.equals("regex") && args.length == 2) { //non-standard
				return regex(argVals[0], argVals[1]);
			} else if (name.equals("depend") && args.length >= 1) { //non-standard
				return argVals[0];
			} else if (name.equals("random") && args.length == 0) { //non-standard
				//calculated expressions may be recomputed w/o warning! use with caution!!
				return new Double(MathUtils.getRand().nextDouble());
			} else if (name.equals("uuid") && (args.length == 0 || args.length == 1)) { //non-standard
				//calculated expressions may be recomputed w/o warning! use with caution!!
				if(args.length == 0) {
					return PropertyUtils.genUUID();
				}
				
				int len = toInt(argVals[0]).intValue();			
				return PropertyUtils.genGUID(len);
			} else if (name.equals("pow") && (args.length == 2)) { //XPath 3.0
				return power(argVals[0], argVals[1]);
			}  else {
				//check for custom handler
				IFunctionHandler handler = (IFunctionHandler)funcHandlers.get(name);
				if (handler != null) {
					return evalCustomFunction(handler, argVals, evalContext);
				} else {
					throw new XPathUnhandledException("function \'" + name + "\'");
				}
			}
			
		//Specific list of issues that we know come up	
		} catch (ClassCastException cce) {
			String args = "";
			for(int i = 0; i < argVals.length ; ++i ){
				args += "'" + String.valueOf(unpack(argVals[i]))+ "'" + (i == argVals.length - 1 ? "" : ", ");
			}
			
			throw new XPathException("There was likely an invalid argument to the function '" + name + "'. The final list of arguments were: [" + args + "]" + ". Full error " + cce.getMessage());
		}
	}

	/**
	 * Given a handler registered to handle the function, try to coerce the function arguments into
	 * one of the prototypes defined by the handler. If no suitable prototype found, throw an eval
	 * exception. Otherwise, evaluate.
	 * 
	 * Note that if the handler supports 'raw args', it will receive the full, unaltered argument
	 * list if no prototype matches. (this lets functions support variable-length argument lists)
	 * 
	 * @param handler
	 * @param args
	 * @return
	 */
	private static Object evalCustomFunction (IFunctionHandler handler, Object[] args, EvaluationContext ec) {
		Vector prototypes = handler.getPrototypes();
		Enumeration e = prototypes.elements();
		Object[] typedArgs = null;

		while (typedArgs == null && e.hasMoreElements()) {
			typedArgs = matchPrototype(args, (Class[])e.nextElement());
		}

		if (typedArgs != null) {
			return handler.eval(typedArgs, ec);
		} else if (handler.rawArgs()) {
			return handler.eval(args, ec);  //should we have support for expanding nodesets here?
		} else {
			throw new XPathTypeMismatchException("for function \'" + handler.getName() + "\'");
		}
		
	}
	
	/**
	 * Given a prototype defined by the function handler, attempt to coerce the function arguments
	 * to match that prototype (checking # args, type conversion, etc.). If it is coercible, return
	 * the type-converted argument list -- these will be the arguments used to evaluate the function.
	 * If not coercible, return null.
	 * 
	 * @param args
	 * @param prototype
	 * @return
	 */
	private static Object[] matchPrototype (Object[] args, Class[] prototype) {
		Object[] typed = null;

		if (prototype.length == args.length) {
			typed = new Object[args.length];

			for (int i = 0; i < prototype.length; i++) {
				typed[i] = null;

				//how to handle type conversions of custom types?
				if (prototype[i].isAssignableFrom(args[i].getClass())) {
					typed[i] = args[i];
				} else {
					try {
						if (prototype[i] == Boolean.class) {
							typed[i] = toBoolean(args[i]);
						} else if (prototype[i] == Double.class) {
							typed[i] = toNumeric(args[i]);
						} else if (prototype[i] == String.class) {
							typed[i] = toString(args[i]);
						} else if (prototype[i] == Date.class) {
							typed[i] = toDate(args[i]);
						}
					} catch (XPathTypeMismatchException xptme) { /* swallow type mismatch exception */ }
				}

				if (typed[i] == null)
					return null;
			}
		}

		return typed;
	}
	
	/******** HANDLERS FOR BUILT-IN FUNCTIONS ********
	 * 
	 * the functions below are the handlers for the built-in xpath function suite
	 * 
	 * if you add a function to the suite, it should adhere to the following pattern:
	 * 
	 *   * the function takes in its arguments as objects (DO NOT cast the arguments when calling
	 *     the handler up in eval() (i.e., return stringLength((String)argVals[0])  <--- NO!)
	 *     
	 *   * the function converts the generic argument(s) to the desired type using the built-in
	 *     xpath type conversion functions (toBoolean(), toNumeric(), toString(), toDate())
	 *     
	 *   * the function MUST return an object of type Boolean, Double, String, or Date; it may
	 *     never return null (instead return the empty string or NaN)
	 *   
	 *   * the function may throw exceptions, but should try as hard as possible not to, and if
	 *     it must, strive to make it an XPathException
	 * 
	 */
	
	public static boolean isNull (Object o) {
		if (o == null) {
			return true; //true 'null' values aren't allowed in the xpath engine, but whatever
		} else if (o instanceof String && ((String)o).length() == 0) {
			return true;
		} else if (o instanceof Double && ((Double)o).isNaN()) {
			return true;
		} else {
			return false;
		}
	}
	
	public static Double stringLength (Object o) {
		String s = toString(o);
		if(s == null) {
			return new Double(0.0);
		}
		return new Double(s.length());
	}
	
	/**
	 * convert a value to a boolean using xpath's type conversion rules
     *
	 * @param o
	 * @return
	 */
	public static Boolean toBoolean (Object o) {
		Boolean val = null;
		
		o = unpack(o);
		
		if (o instanceof Boolean) {
			val = (Boolean)o;
		} else if (o instanceof Double) {
			double d = ((Double)o).doubleValue();
			val = new Boolean(Math.abs(d) > 1.0e-12 && !Double.isNaN(d));
		} else if (o instanceof String) {
			String s = (String)o;
			val = new Boolean(s.length() > 0);
		} else if (o instanceof Date) {
			val = Boolean.TRUE;
		} else if (o instanceof IExprDataType) {
			val = ((IExprDataType)o).toBoolean();
		}
		
		if (val != null) {
			return val;
		} else {
			throw new XPathTypeMismatchException("converting to boolean");
		}
	}
	
	public static Double toDouble(Object o ){
		if(o instanceof Date) {
			return DateUtils.fractionalDaysSinceEpoch((Date)o);
		} else {
			return toNumeric(o);
		}
	}
	
	/**
	 * convert a value to a number using xpath's type conversion rules (note that xpath itself makes
	 * no distinction between integer and floating point numbers)
	 * 
	 * @param o
	 * @return
	 */
	public static Double toNumeric (Object o) {
		Double val = null;
		
		o = unpack(o);
		
		if (o instanceof Boolean) {
			val = new Double(((Boolean)o).booleanValue() ? 1 : 0);
		} else if (o instanceof Double) {
			val = (Double)o;
		} else if (o instanceof String) {
			/* annoying, but the xpath spec doesn't recognize scientific notation, or +/-Infinity
			 * when converting a string to a number
			 */
			
			String s = (String)o;
			double d;
			try {
				s = s.trim();
				for (int i = 0; i < s.length(); i++) {
					char c = s.charAt(i);
					if (c != '-' && c != '.' && (c < '0' || c > '9'))
						throw new NumberFormatException();
				}
				
				d = Double.parseDouble(s);
				val = new Double(d);
			} catch (NumberFormatException nfe) {
				val = new Double(Double.NaN);
			}
		} else if (o instanceof Date) {
			val = new Double(DateUtils.daysSinceEpoch((Date)o));
		} else if (o instanceof IExprDataType) {
			val = ((IExprDataType)o).toNumeric();
		}
		
		if (val != null) {
			return val;
		} else {
			throw new XPathTypeMismatchException("converting '" + (o == null ? "null" : o.toString()) + "' to numeric");
		}
	}

	/**
	 * convert a number to an integer by truncating the fractional part. if non-numeric, coerce the
	 * value to a number first. note that the resulting return value is still a Double, as required
	 * by the xpath engine
	 * 
	 * @param o
	 * @return
	 */
	public static Double toInt (Object o) {
		Double val = toNumeric(o);
		
		if (val.isInfinite() || val.isNaN()) {
			return val;
		} else if (val.doubleValue() >= Long.MAX_VALUE || val.doubleValue() <= Long.MIN_VALUE) {
			return val;
		} else {
			long l = val.longValue();
			Double dbl = new Double(l);
			if (l == 0 && (val.doubleValue() < 0. || val.equals(new Double(-0.)))) {
				dbl = new Double(-0.);
			}
			return dbl;
		}
	}
	
	/**
	 * convert a value to a string using xpath's type conversion rules
	 * 
	 * @param o
	 * @return
	 */
	public static String toString (Object o) {
		String val = null;
		
		o = unpack(o);
		
		if (o instanceof Boolean) {
			val = (((Boolean)o).booleanValue() ? "true" : "false");
		} else if (o instanceof Double) {
			double d = ((Double)o).doubleValue();
			if (Double.isNaN(d)) {
				val = "NaN";
			} else if (Math.abs(d) < 1.0e-12) {
				val = "0";
			} else if (Double.isInfinite(d)) {
				val = (d < 0 ? "-" : "") + "Infinity";
			} else if (Math.abs(d - (int)d) < 1.0e-12) {
				val = String.valueOf((int)d);
			} else {
				val = String.valueOf(d);
			}
		} else if (o instanceof String) {
			val = (String)o;
		} else if (o instanceof Date) {
			val = DateUtils.formatDate((Date)o, DateUtils.FORMAT_ISO8601);
		} else if (o instanceof IExprDataType) {
			val = ((IExprDataType)o).toString();
		}
			
		if (val != null) {
			return val;
		} else {
			if(o == null) { 
				throw new XPathTypeMismatchException("attempt to cast null value to string");
			} else {
				throw new XPathTypeMismatchException("converting object of type " + o.getClass().toString() + " to string");
			}
		}
	}

	/**
	 * convert a value to a date. note that xpath has no intrinsic representation of dates, so this
	 * is off-spec. dates convert to strings as 'yyyy-mm-dd', convert to numbers as # of days since
	 * the unix epoch, and convert to booleans always as 'true'
	 * 
	 * string and int conversions are reversable, however:
	 *   * cannot convert bool to date
	 *   * empty string and NaN (xpath's 'null values') go unchanged, instead of being converted
	 *     into a date (which would cause an error, since Date has no null value (other than java
	 *     null, which the xpath engine can't handle))
	 *   * note, however, than non-empty strings that aren't valid dates _will_ cause an error
	 *     during conversion
	 * 
	 * @param o
	 * @return
	 */
	public static Object toDate (Object o) {
		o = unpack(o);
		
		if (o instanceof Double) {
			Double n = toInt(o);
			
			if (n.isNaN()) {
				return n;
			}
			
			if (n.isInfinite() || n.doubleValue() > Integer.MAX_VALUE || n.doubleValue() < Integer.MIN_VALUE) {
				throw new XPathTypeMismatchException("converting out-of-range value to date");				
			}

			return DateUtils.dateAdd(DateUtils.getDate(1970, 1, 1), n.intValue());
		} else if (o instanceof String) {
			String s = (String)o;
			
			if (s.length() == 0) {
				return s;
			}
			
			Date d = DateUtils.parseDateTime(s);
			if (d == null) {
				throw new XPathTypeMismatchException("converting string " + s + " to date");
			} else {
				return d;
			}
		} else if (o instanceof Date) {
			return DateUtils.roundDate((Date)o);
		} else {
			String type = o == null ? "null" : o.getClass().getName();
			throw new XPathTypeMismatchException("converting unexpected type " + type + " to date");
		}
	}

	public static Boolean boolNot (Object o) {
		boolean b = toBoolean(o).booleanValue();
		return new Boolean(!b);
	}
	
	public static Boolean boolStr (Object o) {
		String s = toString(o);
		if (s.equalsIgnoreCase("true") || s.equals("1"))
			return Boolean.TRUE;
		else
			return Boolean.FALSE;
	}

	public static String dateStr (Object od, Object of) {
		if(od instanceof Date) {
			//There's no reason to pull out the time info here if 
			//this is already a date (might still have time info
			//that we want to preserve).
			//we round at all of the relevant points up to here,
			//and we only print out the date info when asked anyway.
		} else {
			od = toDate(od);
		}
		if (od instanceof Date) {
			return DateUtils.format((Date)od, toString(of));
		} else {
			return "";
		}
	}
	

	private Double position(TreeReference refAt) {
		return new Double(refAt.getMultLast());
	}
	
	public static Object ifThenElse (DataInstance model, EvaluationContext ec, XPathExpression[] args, Object[] argVals) {
		argVals[0] = args[0].eval(model, ec);
		boolean b = toBoolean(argVals[0]).booleanValue();
		return (b ? args[1].eval(model, ec) : args[2].eval(model, ec));
	}
	
	/**
	 * return whether a particular choice of a multi-select is selected
	 * 
	 * @param o1 XML-serialized answer to multi-select question (i.e, space-delimited choice values)
	 * @param o2 choice to look for
	 * @return
	 */
	public static Boolean multiSelected (Object o1, Object o2) {
		o2 = unpack(o2);
		if(!(o2 instanceof String)) {
			throw generateBadArgumentMessage("selected", 2, "single potential value from the list of select options", o2);
		}
		String s1 = (String)unpack(o1);
		String s2 = ((String)o2).trim();
		
		return new Boolean((" " + s1 + " ").indexOf(" " + s2 + " ") != -1);
	}
	
	public static XPathException generateBadArgumentMessage(String functionName, int argNumber, String type, Object endValue) {
		return new XPathException("Bad argument to function '" + functionName +"'. Argument #" + argNumber +" should be a " + type + ", but instead evaluated to: " + String.valueOf(endValue));
	}

	/**
	 * return the number of choices in a multi-select answer
	 * 
	 * @param o XML-serialized answer to multi-select question (i.e, space-delimited choice values)
	 * @return
	 */
	public static Double countSelected (Object o) {
		
		if(!(unpack(o) instanceof String)){
			throw new XPathTypeMismatchException("count-selected argument was not a select list");	
		}

		String s = (String)unpack(o);
		return new Double(DateUtils.split(s, " ", true).size());
	}
	
	/**
	 * Get the Nth item in a selected list
	 * 
	 * @param o1 XML-serialized answer to multi-select question (i.e, space-delimited choice values)
	 * @param o2 the integer index into the list to return
	 * @return
	 */
	public static String selectedAt (Object o1, Object o2) {
		String selection = (String)unpack(o1);
		int index = toInt(o2).intValue();
		
		return DateUtils.split(selection, " ", true).elementAt(index);
	}
	
	/**
	 * count the number of nodes in a nodeset
	 * 
	 * @param o
	 * @return
	 */
	public static Double count (Object o) {
		if (o instanceof XPathNodeset) {
			return new Double(((XPathNodeset)o).size());
		} else {
			throw new XPathTypeMismatchException("not a nodeset");
		}	
	}

	/**
	 * sum the values in a nodeset; each element is coerced to a numeric value
	 * 
	 * @param model
	 * @param o
	 * @return
	 */
	public static Double sum (Object argVals[]) {
		double sum = 0.0;
		for (int i = 0; i < argVals.length; i++) {
			sum += toNumeric(argVals[i]).doubleValue();
		}
		return new Double(sum);
	}
	
	/**
	 * Identify the largest value from the list of provided values.
	 * 
	 * @param argVals
	 * @return
	 */
	private static Object max(Object[] argVals) {
		double max = Double.MIN_VALUE;
		for (int i = 0; i < argVals.length; i++) {
			max = Math.max(max, toNumeric(argVals[i]).doubleValue());
		}
		return new Double(max);
	}
	
	private static Object min(Object[] argVals) {
		double min = Double.MAX_VALUE;
		for (int i = 0; i < argVals.length; i++) {
			min = Math.min(min, toNumeric(argVals[i]).doubleValue());
		}
		return new Double(min);
	}

	/**
	 * concatenate an abritrary-length argument list of string values together
	 * 
	 * @param argVals
	 * @return
	 */
	public static String join (Object oSep, Object[] argVals) {
		String sep = toString(oSep);
		StringBuffer sb = new StringBuffer();
		
		for (int i = 0; i < argVals.length; i++) {
			sb.append(toString(argVals[i]));
			if (i < argVals.length - 1)
				sb.append(sep);
		}
		
		return sb.toString();
	}
	
	/*
	 * Implementation decisions:
	 * -Returns the empty string if o1.equals("")
	 * -Returns the empty string for any inputs that would 
	 * cause an IndexOutOfBoundsException on call to Java's substring method, 
	 * after start and end have been adjusted
	 */
	public static String substring (Object o1, Object o2, Object o3) {
		String s = toString(o1);
		
		if (s.length() == 0) {
			return "";
		}
		
		int start = toInt(o2).intValue();
		
		int len = s.length();

		int end = (o3 != null ? toInt(o3).intValue() : len);		
		if (start < 0) {
			start = len + start;
		}
		if (end < 0) {
			end = len + end;
		}
		start = Math.min(Math.max(0, start), end);
		end = Math.min(Math.max(0, end), end);
		
		return ((start <= end && end <= len) ? s.substring(start, end) : "");
	}
	
	/**
	 * perform a 'checklist' computation, enabling expressions like 'if there are at least 3 risk
	 * factors active'
	 * 
	 * @param argVals
	 *   the first argument is a numeric value expressing the minimum number of factors required.
	 *     if -1, no minimum is applicable
	 *   the second argument is a numeric value expressing the maximum number of allowed factors.
	 *     if -1, no maximum is applicalbe
	 *   arguments 3 through the end are the individual factors, each coerced to a boolean value
	 * @return true if the count of 'true' factors is between the applicable minimum and maximum,
	 *   inclusive
	 */
	public static Boolean checklist (Object oMin, Object oMax, Object[] factors) {
		int min = toNumeric(oMin).intValue();
		int max = toNumeric(oMax).intValue();
		
		int count = 0;
		for (int i = 0; i < factors.length; i++) {
			if (toBoolean(factors[i]).booleanValue())
				count++;
		}
		
		return new Boolean((min < 0 || count >= min) && (max < 0 || count <= max));
	}

	/**
	 * very similar to checklist, only each factor is assigned a real-number 'weight'.
	 * 
	 * the first and second args are again the minimum and maximum, but -1 no longer means
	 * 'not applicable'.
	 * 
	 * subsequent arguments come in pairs: first the boolean value, then the floating-point
	 * weight for that value
	 * 
	 * the weights of all the 'true' factors are summed, and the function returns whether
	 * this sum is between the min and max
	 * 
	 * @param argVals
	 * @return
	 */
	public static Boolean checklistWeighted (Object oMin, Object oMax, Object[] flags, Object[] weights) {
		double min = toNumeric(oMin).doubleValue();
		double max = toNumeric(oMax).doubleValue();
		
		double sum = 0.;
		for (int i = 0; i < flags.length; i++) {
			boolean flag = toBoolean(flags[i]).booleanValue();
			double weight = toNumeric(weights[i]).doubleValue();
			
			if (flag)
				sum += weight;
		}
		
		return new Boolean(sum >= min && sum <= max);
	}
	
	/**
	 * determine if a string matches a regular expression. 
	 * 
	 * @param o1 string being matched
	 * @param o2 regular expression
	 * @return
	 */
	public static Boolean regex (Object o1, Object o2) {
		String str = toString(o1);
		String re = toString(o2);
		
		RE regexp = new RE(re);
		boolean result = regexp.match(str);
		return new Boolean(result);
	}

	private static Object[] subsetArgList (Object[] args, int start) {
		return subsetArgList(args, start, 1);
	}
	
	/**
	 * return a subset of an argument list as a new arguments list
	 * 
	 * @param args
	 * @param start index to start at
	 * @param skip sub-list will contain every nth argument, where n == skip (default: 1)
	 * @return
	 */
	private static Object[] subsetArgList (Object[] args, int start, int skip) {
		if (start > args.length || skip < 1) {
			throw new RuntimeException("error in subsetting arglist");
		}
		
		Object[] subargs = new Object[(int)MathUtils.divLongNotSuck(args.length - start - 1, skip) + 1];
		for (int i = start, j = 0; i < args.length; i += skip, j++) {
			subargs[j] = args[i];
		}
		
		return subargs;
	}
	
	public static Object unpack (Object o) {
		if (o instanceof XPathNodeset) {
			return ((XPathNodeset)o).unpack();
		} else {
			return o;
		}
	}
	
	/**
	 * 
	 */
	public Object pivot (DataInstance model, EvaluationContext evalContext, Vector<Object> pivots, Object sentinal) throws UnpivotableExpressionException {
		String name = id.toString();
		
		//for now we'll assume that all that functions do is return the composition of their components
		Object[] argVals = new Object[args.length];
		
		
		//Identify whether this function is an identity: IE: can reflect back the pivot sentinal with no modification
		String[] identities = new String[] {"string-length"}; 
		boolean id = false;
		for(String identity : identities) {
			if(identity.equals(name)) {
				id = true;
			}
		}
		
		//get each argument's pivot
		for (int i = 0; i < args.length; i++) {
			argVals[i] = args[i].pivot(model, evalContext, pivots, sentinal);
		}
		
		boolean pivoted = false;
		//evaluate the pivots
		for(int i = 0 ; i < argVals.length; ++i) {
			if(argVals[i] == null) {
				//one of our arguments contained pivots,  
				pivoted = true;
			} else if(sentinal.equals(argVals[i])) {
				//one of our arguments is the sentinal, return the sentinal if possible
				if(id) {
					return sentinal;
				} else {
					//This function modifies the sentinal in a way that makes it impossible to capture
					//the pivot.
					throw new UnpivotableExpressionException();
				}
			}
		}
		
		if(pivoted) {
			if(id) {
				return null;
			} else {
				//This function modifies the sentinal in a way that makes it impossible to capture
				//the pivot.
				throw new UnpivotableExpressionException();
			}
		}
		
		//TODO: Inner eval here with eval'd args to improve speed
		return eval(model, evalContext);
		
	}
	/**
	 * Best faith effort at getting a result for math.pow 
	 * 
	 * @param o1 The base number
	 * @param o2 The exponent of the number that it is to be raised to
	 * @return An approximation of o1 ^ o2. If there is a native power
	 * function, it is utilized. It there is not, a recursive exponent is
	 * run if (b) is an integer value, and a taylor series approximation is 
	 * used otherwise. 
	 */
	private Double power(Object o1, Object o2) {	
		//#if polish.cldc
		//# //CLDC doesn't support craziness like "power" functions, so we're on our own.
		//# return powerApprox(o1, o2);
		//#else 
		//Just use the native lib! should be available.
		double a = toDouble(o1).doubleValue();
		double b = toDouble(o2).doubleValue();

		return Math.pow(a, b);
		//#endif
	}
	
	private Double powerApprox(Object o1, Object o2) {
		double a = toDouble(o1).doubleValue();
		Double db = toDouble(o2);
		//We need to determine if "b" is a double, or an integer.
		if(Math.abs(db.doubleValue()- toInt(db).doubleValue()) > DOUBLE_TOLERANCE) {
			throw new XPathUnsupportedException("Sorry, power functions with non-integer exponents are not supported on your platform");
		} else {
			//Integer it is, whew!
			int b = db.intValue();
			//One last check. If b is negative, we need to invert A, 
			//and then do the exponent.
			if(b < 0) {
				b = -b;
				a = 1.0 / a;
			}
			//Ok, now we can do a simple recursive solution
			return power(a, b);
		}
	}
	
	private static Double power(double a, int b) {
		if(b == 0) { return new Double(1.0); }
		double ret = a;
		for(int i = 1 ; i < b ; ++i) {
			ret *= a;
		}
		return new Double(ret);
	}
	
	/**
	 * This code is fairly legit, but it not compliant with actual
	 * floating point math reqs. I don't know whether we 
	 * should expose the option of using it, exactly. 
	 * 
	 *  
	 * @param a
	 * @param b
	 * @return
	 */
	public static double pow(final double a, final double b) {
		final long tmp = Double.doubleToLongBits(a);
		final long tmp2 = (long)(b * (tmp - 4606921280493453312L)) + 4606921280493453312L;
		return Double.longBitsToDouble(tmp2);
	}
	
	public static final double DOUBLE_TOLERANCE = 1.0e-12;
}
