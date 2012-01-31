package com.labs.jmvc;

import org.json.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration
 * @author Benjamin Dezile
 */
public class Config {

	private static final String appConfigFile = "config/app.conf";			// Application config file
	private static final String modelConfigFile = "config/model.conf";		// Application config file
	private static final String configFile = "config/env.${env}.conf";		// Environment config file
	private static final String indentationChar = "\t";						// Character used for indenting
	private static Config instance = new Config();							// Instance
	
	private Map<String,Object> values;										// Configuration values
	private String env;
	
	private Config() {
		values = new HashMap<String,Object>(0);
		instance = this;
		load(appConfigFile);
		load(modelConfigFile);
		env = Config.get("application", "env");
		load(configFile.replace("${env}", env));
	}
	
	/**
	 * Reload configuration
	 */
	public static synchronized void load() {
		instance = new Config();
	}
	
	/**
	 * Create a new config object by wrapping aroung section values
	 * @param sectionValues {@link Map}<{@link String}, {@link Object}> - Section values
	 */
	private Config(Map<String,Object> sectionValues) {
		values = sectionValues;
	}
	
	/**
	 * Load config from file
	 * @param filepath {@link String} - File to load from
	 * @return {@link Config}
	 */
	private void load(String filepath) {
		BufferedReader reader = null;
		StringBuffer lastLine = new StringBuffer();
		String line;
		try {
			InputStream in = Config.class.getClassLoader().getResourceAsStream(filepath);
			reader = new BufferedReader(new InputStreamReader(in));
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.endsWith(":")) {
					values.put(line.substring(0, line.length() - 1), readSection(reader, 1, lastLine));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try { reader.close(); } catch (Exception e) {}
			}
		}
	}
	
	/**
	 * Parse a configuration section
	 * @param reader {@link BufferedReader} - Reader
	 * @param ind int - Indentation level for the level that is about to be read
	 * @param lastLine {@link StringBuffer} - Last line read in the section 
	 * @return {@link Map}<{@link String},{@link Object}>
	 * @throws IOException 
	 */
	private Map<String,Object> readSection(BufferedReader reader, int ind, StringBuffer lastLine) throws IOException {
		String line;
		int p,q,r;
		Map<String,Object> section = new HashMap<String,Object>(0);
		while (true) {
			line = reader.readLine();
			if (line == null) {
				/* End of file */
				break;
			}
			if ("".equals(line.trim()) || line.indexOf("-") < ind) {
				/* End of section */
				lastLine.append(line);
				break;
			}
			if (line.trim().endsWith(":")) {
				/* Start of a subsection */
				p = line.indexOf("-");
				q = line.indexOf(":", p);
				section.put(line.substring(p+1, q).trim(), readSection(reader, ind + 1, lastLine));
				if ("".equals(lastLine.toString().trim())) {
					/* End of section reached while reading subsection */
					lastLine = new StringBuffer();
					break;
				}
			} else {
				/* Keep on reading the current section */
				line = line.trim().replace(indentationChar, "");
				p = line.indexOf("-");
				q = line.indexOf(":", p);
				if ((r = line.indexOf("\n", q)) < 0) {
					r = line.length();
				}
				if (p >= 0 && q > p && r > q) {
					section.put(line.substring(p+1, q).trim(), line.substring(q+1, r).trim());
				}
			}
		}
		return section;
	}
	
	/**
	 * Evaluate config field value (i.e. resolve references)
	 * @param value {@link String} - Field value  
	 * @param map {@link Map}<{@link String},{@link Object}> - Map it belongs to
	 * @return {@link String}
	 */
	@SuppressWarnings("unchecked")
	private static String evaluate(String value, Map<String,Object> map) {
		int p = -1, q = -1, oldLength = 0;
		StringBuffer eval = new StringBuffer(value);
		while ((p=eval.indexOf("${", p+1)) >= 0) {
			q = eval.indexOf("}", p);
			String var = eval.substring(p+2, q);
			String varValue;
			if (var.indexOf(".") > 0) {
				/* External reference TODO: Add support for multi level reference (e.g. app.foo.bar.val)*/
				String[] parts = var.split("\\.");
				varValue = ((Map)instance.values.get(parts[0])).get(parts[1]).toString();
			} else {
				/* Same level reference */
				varValue = (map != null ? map : instance.values).get(var).toString();
			}
			if (varValue.indexOf("${") >= 0) {
				varValue = evaluate(varValue, map);
			}
			oldLength = eval.length();
			eval.replace(p, q+1, varValue.toString());
			p = q - (oldLength - eval.length());
		}
		return eval.toString();
	}
	
	/**
	 * Put a new config entry
	 * @param section {@link String} - Section
	 * @param key {@link String} - Entry key
	 * @param val {@link Object} - Entry value
	 */
	@SuppressWarnings("unchecked")
	protected void put(String section, String key, Object val) {
		if (values.containsKey(section)) {
			((Map)values.get(section)).put(key, val);
		} else {
			throw new IllegalArgumentException("Section not found: " + section);
		}
	}
	
	/**
	 * Get a config value
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Field
	 * @return {@link Object}
	 */
	@SuppressWarnings("unchecked")
	protected static Object getObject(String section, String key) {
		Map<String,Object> parentMap = null;
		Object val = null;
		if (section == null) {
			val = instance.values.get(key);
		} else if (instance.values.containsKey(section)) {
			parentMap = (Map)instance.values.get(section);
			val = parentMap.get(key);
		} else {
			throw new IllegalArgumentException("Section not found: " + section);
		}
		if (val instanceof String) {
			val = evaluate((String)val, parentMap);
		}
		return val;
	}
		
	/**
	 * Get a config value
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Field
	 * @return {@link String}
	 */
	public static String get(String section, String key) {
		Object val = getObject(section, key);
		if (val != null) {
			if (!(val instanceof String)) {
				val = val.toString();
				instance.put(section, key, val);
			}
			return (String)val;
		}
		return null;
	}
	
	/**
	 * Get a config value
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Field
	 * @return {@link Integer}
	 */
	public static Integer getInt(String section, String key) {
		Object val = getObject(section, key);
		if (val != null) {
			if (!(val instanceof Integer)) {
				val = Integer.parseInt(val.toString());
				instance.put(section, key, val);
			}
			return (Integer)val;
		}
		return null;
	}
	
	/**
	 * Get a config value
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Field
	 * @return {@link Long}
	 */
	public static Long getLong(String section, String key) {
		Object val = getObject(section, key);
		if (val != null) {
			if (!(val instanceof Long)) {
				val = Long.parseLong(val.toString());
				instance.put(section, key, val);
			}
			return (Long)val;
		}
		return null;
	}

	/**
	 * Get a config value
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Field
	 * @return {@link Boolean}
	 */
	public static Boolean getBool(String section, String key) {
		Object val = getObject(section, key);
		if (val != null) {
			if (!(val instanceof Boolean)) {
				val = Boolean.parseBoolean(val.toString());
				instance.put(section, key, val);
			}
			return (Boolean)val;
		}
		return null;
	}
	
	/**
	 * Get a config value
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Field
	 * @return {@link String}[]
	 */
	public static String[] getArray(String section, String key) {
		Object val = getObject(section, key);
		if (val != null) {
			if (!val.getClass().isArray()) {
				val = ((String)val).split(",|,\\s|\\s,");
				instance.put(section, key, val);
			}
			return (String[])val;
		}
		return null;
	}
	
	/**
	 * Get a config sub section
	 * @param section {@link String} - Config section
	 * @param key {@link String} - Sub section
	 * @return {@link Config}
	 */
	@SuppressWarnings("unchecked")
	public static Config getSubSection(String section, String key) {
		Object val = getObject(section, key);
		if (val != null) {
			if (!(val instanceof Config)) {
				val = new Config((Map)val);
				instance.put(section, key, val);
			}
			return (Config)val;
		}
		return null;
	}
	
	/**
	 * Get directly from the instance
	 * @param key {@link String} - Value to get
	 * @return {@link Object}
	 */
	public Object get(String key) {
		Object val = values.get(key);
		if (val instanceof String) {
			val = evaluate((String)val, values);
		}
		return val;
	}
	
	/**
	 * Return the global config map
	 * @return {@link Map}<{@link String},{@link Object}>
	 */
	public static Map<String,Object> getGlobalMap() {
		return instance.values;
	}
	
	/**
	 * Return the config map
	 * @return {@link Map}<{@link String},{@link Object}>
	 */
	public Map<String,Object> getMap() {
		return values;
	}
	
	/**
	 * Return the config keys
	 * @return {@link Set}<{@link String}>
	 */
	public static Set<String> getKeys() {
		return instance.values.keySet();
	}
	
	
	/*************  HELPERS  ***************/
	
	public static boolean isDev() {
		return "dev".equals(instance.env);
	}

	public static boolean isStaging() {
		return "staging".equals(instance.env);
	}
	
	public static boolean isProd() {
		return "prod".equals(instance.env);
	}
	
	public static String getVersion() {
		return get("application", "version");
	}
	
	public static int getVersionNumber() {
		String v = get("application", "version");
		int n = v.hashCode();
		return (n >= 0 ? n : n + Integer.MAX_VALUE);
	}
	
	/**
	 * Return a JSON representation of the global config map
	 * @return {@link JSONObject}
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static JSONObject toJSON() throws Exception {
		JSONObject json = new JSONObject();
		for (String sectionKey:instance.values.keySet()) {
			Map<String,Object> sectionMap = (Map)instance.values.get(sectionKey);
			json.put(sectionKey, toJSON(sectionMap));
		}
		return json;
	}
	
	/**
	 * Return a JSON representation of the given section map
	 * @param map {@link Map}<{@link String},{@link Object}> - Config section map
	 * @return {@link JSONObject}
	 * @throws Exception
	 */
	private static JSONObject toJSON(Map<String,Object> map) throws Exception {
		JSONObject sectionJson = new JSONObject();
		for (String key:map.keySet()) {
			Object value = map.get(key);
			if (value instanceof Config) {
				/* Subsection */
				Config c = (Config)value;
				sectionJson.put(key, toJSON(c.getMap()));
			} else if (value.getClass().isArray()) {
				/* Array */
				JSONArray jsonArray = new JSONArray();
				for (int i=0;i<Array.getLength(value);i++) {
					jsonArray.put(Array.get(value, i));
				}
				sectionJson.put(key, jsonArray);
			} else {
				/* Primitive */
				if (value instanceof String) {
					value = evaluate((String)value, map);
				}
				sectionJson.put(key, value);
			}
		}
		return sectionJson;
	}
	
}
