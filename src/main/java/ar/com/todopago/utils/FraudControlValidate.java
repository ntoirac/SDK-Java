package ar.com.todopago.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ar.com.todopago.api.ElementNames;

public class FraudControlValidate {

	private final String PUNCT = "\\p{Punct}";
	private final String ASCII = "\\P{ASCII}";
	private final String PHONE = "[^0-9]";
	private final String CSIT_DESCRIPTION = "CSITPRODUCTDESCRIPTION";
	private final String CSBTSTATE = "CSBTSTATE";
	private final String NUMERAL = "#";	
	private final String FIELD = "field";
	private final String VALIDATE = "validate";
	private final String FORMAT = "format";
	private final String FUNCTION = "function";
	private final String MESSAGE = "message";
	private final String PARAMS = "params";
	private final String DEFAULT = "default";	
	private final String LOCATION_JSON = "/validations.json";
	
	private JSONArray jsonArray;
	private Map<String, String> resultMap;
	private Map<String, String> CSITMap;
	private Map<String, String> stateCode;
	private List<String> campError;
	private Map<String, Integer> keyMap;

	public FraudControlValidate() {

		String line;
		StringBuilder text = new StringBuilder();

		try {

			InputStream is = FraudControlValidate.class.getResourceAsStream(LOCATION_JSON);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

			while ((line = reader.readLine()) != null) {
				text.append(line).append(" ");
			}
			this.jsonArray = new JSONArray(text.toString());
			this.resultMap = new HashMap<String, String>();
			this.CSITMap = new HashMap<String, String>();
			this.campError = new ArrayList<String>();
			setState();
			setKeyMap();

		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Map<String, String> validate(Map<String, String> parameters) {

		Iterator<Entry<String, String>> it = parameters.entrySet().iterator();
		Entry<String, String> entry = null;

		try {

			while (it.hasNext()) {
				entry = it.next();
				String key = entry.getKey();
				String value = entry.getValue();

				for (int i = 0; i < this.jsonArray.length(); i++) {
					JSONObject json = jsonArray.getJSONObject(i);
					String field = json.getString(FIELD);
					if (key.equals(field)) {
						JSONArray validateArray = null;
						JSONArray formatArray = null;
						Iterator<String> Itr = json.keys();
						while (Itr.hasNext()) {
							String name = Itr.next();
							if (name.equals(VALIDATE)) {
								validateArray = json.getJSONArray(VALIDATE);
							}
							if (name.equals(FORMAT)) {
								formatArray = json.getJSONArray(FORMAT);
							}
						}
						selector(validateArray, formatArray, value, field);
					}
				}
			}
			
			Map<String, String> csitMap = csitFormat(254);
			resultMap.putAll(csitMap);
			String error = "";
			for (String field : this.campError) {
				error = error + field + ", " ;
			}			
			if(this.campError.size()>0){
				resultMap.put(ElementNames.ERROR, error);
			}
			
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return resultMap;

	}

	private void selector(JSONArray validateArray, JSONArray formatArray, String value, String field)
			throws JSONException {

		boolean validationOK = false;

		if (validateArray != null) {
			validationOK = validation(validateArray, value, field);
			if (validationOK) {
				if(formatArray != null){
					value = format(formatArray, value, field);
				}				
			} else {
				addError(field);
			}

			if (validation(validateArray, value, field)) {
				addField(field, value);
			} else {
				addError(field);
			}

		}else{		
			if(formatArray != null){
				value = format(formatArray, value, field);
				addField(field, value);
			}
			
		}
	}

	private boolean validation(JSONArray validateArray, String value, String field) throws JSONException {

		boolean valid = false;
		JSONObject json = null;
		String val = value;

		for (int i = 0; i < validateArray.length(); i++) {

			String function = null;
			String message = null;
			ArrayList<String> params = new ArrayList<String>();
			String def = null;
			json = validateArray.getJSONObject(i);

			Iterator<String> Itr = json.keys();
			while (Itr.hasNext()) {
				String name = Itr.next();
				if (name.equals(FUNCTION)) {
					function = json.getString(name);
				}
				if (name.equals(MESSAGE)) {
					message = json.getString(name);
				}
				if (name.equals(DEFAULT)) {
					def = setDefaultValue(json.getString(name));
				}
				if (name.equals(PARAMS)) {
					JSONArray array = json.getJSONArray(name);
					for (int x = 0; x < array.length(); x++) {
						params.add(array.getString(x));
					}
				}
			}

			if (function.equals("notEmpty")) {
				valid = isNotEmpty(val);
				if (!valid) {
					if (def != null) {
						addField(field, def);
						val = def;
						valid = true;
					} else {
						addField(field, message);
					}
				}
			}

			if (function.equals("regex")) {
				valid = regexValidate(params.get(0), val);
				if (!valid) {
					if (def != null) {
						addField(field, def);
					} else {
						addField(field, message);
					}
				}
			}

		}
		return valid;
	}

	private String format(JSONArray formatArray, String value, String field) throws JSONException {

		String result = value;
		JSONObject json = null;

		for (int i = 0; i < formatArray.length(); i++) {

			int function = 0 ;
			String message = null;
			ArrayList<String> params = new ArrayList<String>();
			json = formatArray.getJSONObject(i);

			Iterator<String> Itr = json.keys();
			while (Itr.hasNext()) {
				String name = Itr.next();
				if (name.equals(FUNCTION)) {
					String fun = json.getString(name);
					function = keyMap.get(fun);				
				}
				if (name.equals(MESSAGE)) {
					message = json.getString(name);
				}
				if (name.equals(PARAMS)) {
					JSONArray array = json.getJSONArray(name);
					for (int x = 0; x < array.length(); x++) {
						params.add(array.getString(x));
					}
				}
			}

			switch (function) {
			case 1:
				result = clean(value);
				break;

			case 2:
				result = truncate(value, Integer.valueOf(params.get(0)));
				break;

			case 3:
				result = hardcode(value, params.get(0));
				break;

			case 4:
				result = upper(value);
				break;

			case 5:
				result = regexFormat(value, params.get(0));
				break;

			case 6:
				result = phone(value);
				break;

			case 7:
				CSITMap.put(field, value);
				break;

			}
		}

		return result;
	}

	private String hardcode(String value, String hardcode) {
		if (hardcode != null && !hardcode.isEmpty()) {
			value = hardcode;
		}
		return value;
	}

	private boolean isNotEmpty(String value) {
		if (value != null && !value.isEmpty()) {
			return true;
		}
		return false;
	}

	private String truncate(String value, int size) {
		if (value != null && !value.isEmpty()) {
			value.trim();
			if (value.length() > size) {
				value = value.substring(0, size);
			}
		}
		return value;
	}

	private String upper(String value) {
		if (value != null && !value.isEmpty()) {
			value.trim();
			value.toUpperCase();
		}
		return value;
	}

	private String phone(String value) {

		if (value != null && !value.isEmpty()) {

			value = clean(value).trim();
			Pattern patternAcentos = Pattern.compile(PHONE);
			value = patternAcentos.matcher(value).replaceAll("");

			if (value.length() == 8) {
				value = "5411" + value;
			}

			if (value.substring(0).equals("0")) {
				value = "54" + value;
			}

			if (value.length() < 6) {
				value = "5411" + value;
			}
		}
		return value;
	}

	private String clean(String value) {
		if (value != null && !value.isEmpty()) {

			value = value.trim();
			String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
			Pattern patternAcentos = Pattern.compile(ASCII);
			value = patternAcentos.matcher(normalized).replaceAll("");

			Pattern pattern = Pattern.compile(PUNCT);
			value = pattern.matcher(value).replaceAll("");
		}
		return value;
	}

	private boolean regexValidate(String pattern, String value) {

		boolean result = false;

		if (value != null && !value.isEmpty()) {
			Pattern pat = Pattern.compile(pattern);
			Matcher mat = pat.matcher(value);
			if (mat.matches()) {
				result = true;
			}
		}
		return result;
	}

	private String regexFormat(String value,String pattern) {
		if (value != null && !value.isEmpty()) {
			Pattern pat = Pattern.compile(pattern);
			Matcher mat = pat.matcher(value);
			value = pat.matcher(value).replaceAll("");
		}
		return value;
	}

	private Map<String, String> csitFormat(int size) {

		Map<String, String> mapResult = new HashMap<String, String>();
		String value = null;
		int sizeDescription = 0;

		if (CSITMap.containsKey(CSIT_DESCRIPTION)) {
			value = CSITMap.get(CSIT_DESCRIPTION);
			value = cutDescription(value, size);

			String[] aux = value.split(NUMERAL);
			sizeDescription = aux.length;

			Iterator<Entry<String, String>> it = CSITMap.entrySet().iterator();
			Entry<String, String> entry = null;

			while (it.hasNext()) {
				entry = it.next();
				String key = entry.getKey();
				String val = entry.getValue();
				mapResult.put(key, genericCutCsit(val, sizeDescription));
			}

		} else {
			addError(CSIT_DESCRIPTION);
		}

		return mapResult;
	}

	private String cutDescription(String values, int size) {

		String result = "";

		String[] arrayValues = values.split(NUMERAL);
		String[] aux = new String[arrayValues.length];

		int count = arrayValues.length;
		int x = (size / count) - 1;

		if (x >= 20) {
			for (int i = 0; i < arrayValues.length; i++) {
				aux[i] = truncate(arrayValues[i].trim(), x) + NUMERAL;
				result = result + aux[i];
			}
		} else {
			int cantProduct = (size / 21);
			for (int i = 0; i < cantProduct; i++) {
				aux[i] = truncate(arrayValues[i].trim(), 20) + NUMERAL;
				result = result + aux[i];
			}
		}
		result = result.substring(0, result.length() - 1);
		return result;
	}

	private String genericCutCsit(String values, int cant) {

		String result = "";

		String[] arrayValues = values.split(NUMERAL);
		String[] aux = new String[cant];

		for (int i = 0; i < arrayValues.length; i++) {
			if (i < cant) {
				aux[i] = truncate(arrayValues[i].trim(), 20) + NUMERAL;
				result = result + aux[i];
			}
		}

		result = result.substring(0, result.length() - 1);
		return result;
	}

	private void addError(String field) {		
		if(!this.campError.contains(field)){
			this.campError.add(field);		
		}		
	}

	private void addField(String field, String value) {
		if (!resultMap.containsKey(field)) {
			resultMap.put(field, value);
		}
	}

	private String setDefaultValue(String value) {
		String result = null;

		if (value.equals("random")) {
			int C = (int)(Math.random()*1000 + 1);
			result = "ABC" + C;
		} else {
			if (value.equals("findState")) {
				result = findState();
			} else {
				result = value;
			}
		}
		return result;
	}

	private void setState() {
		this.stateCode = new HashMap<String, String>();
		this.stateCode.put("A", "4400");
		this.stateCode.put("B", "1900");
		this.stateCode.put("C", "1000");
		this.stateCode.put("D", "5700");
		this.stateCode.put("E", "3100");
		this.stateCode.put("F", "5300");
		this.stateCode.put("G", "4200");
		this.stateCode.put("H", "3500");
		this.stateCode.put("J", "5400");
		this.stateCode.put("K", "4700");
		this.stateCode.put("L", "6300");
		this.stateCode.put("M", "5500");
		this.stateCode.put("N", "3300");
		this.stateCode.put("P", "3600");
		this.stateCode.put("Q", "8300");
		this.stateCode.put("R", "8500");
		this.stateCode.put("S", "3000");
		this.stateCode.put("T", "4001");
		this.stateCode.put("U", "9103");
		this.stateCode.put("V", "9410");
		this.stateCode.put("W", "3400");
		this.stateCode.put("X", "5000");
		this.stateCode.put("Y", "4600");
		this.stateCode.put("Z", "9400");	
	}
	
	private void setKeyMap() {
		this.keyMap = new HashMap<String, Integer>();
		this.keyMap.put("clean",1);
		this.keyMap.put("truncate",2); 
		this.keyMap.put("hardcode",3);
		this.keyMap.put("upper",4);
		this.keyMap.put("regex",5);
		this.keyMap.put("phone",6);
		this.keyMap.put("csitFormat",7);
	}


	private String findState() {
		String result = "C";

		if (resultMap.containsKey(CSBTSTATE)) {
			if (stateCode.containsKey(resultMap.get(CSBTSTATE))) {
				result = stateCode.get(resultMap.get(CSBTSTATE));
			}
		}
		return result;
	}

}
