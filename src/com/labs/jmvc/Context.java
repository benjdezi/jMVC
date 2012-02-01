package com.labs.jmvc;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.tomcat.util.http.fileupload.DefaultFileItemFactory;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.FileUploadException;

/**
 * Request context
 * @author Benjamin Dezile
 */
public class Context {
	
	protected static final int COOKIE_MAX_AGE = 365*24*3600;
	protected static final String COOKIE_PATH = Config.get("application", "prefix");
	protected static final String defaultMimeType = Config.get("controller", "default_mime");
	
	protected HttpServletRequest request;
	protected HttpServletResponse response;
	protected HttpSession session;
	
	/**
	 * Create a new request context
	 * @param req {@link HttpServletRequest} - Current request
	 */
	public Context(HttpServletRequest req) {
		this(req, null);
	}
	
	/**
	 * Create a new request context
	 * @param req {@link HttpServletRequest} - Current request
	 * @param resp {@link HttpServletResponse} - Response
	 */
	public Context(HttpServletRequest req, HttpServletResponse resp) {
		request = req;
		response = resp;
		session = req.getSession(true);
	}
	
	/**
	 * Get the associated request
	 * @return {@link HttpServletRequest}
	 */
	public HttpServletRequest getRequest() {
		return request;
	}
	
	/**
	 * Get the associated response
	 * @return {@link HttpServletResponse}
	 */
	public HttpServletResponse getResponse() {
		return response;
	}
	
	/**
	 * Get the associated session
	 * @return {@link HttpSession}
	 */
	public HttpSession getSession() {
		return session;
	}
	
	/**
	 * Get a request parameter
	 * @param key {@link String} - Parameter key
	 * @return {@link String}
	 */
	public String getParameter(String key) {
		return request.getParameter(key);
	}
	
	/**
	 * Return whether the request contains the given parameters
	 * @param key {@link String} - Parameter key
	 * @return boolean
	 */
	public boolean hasParameter(String key) {
		return request.getParameterMap().containsKey(key);
	}
	
	/**
	 * Register an object to this context
	 * @param name {@link String} - Associated name
	 * @param obj {@link Object} - Object to register
	 */
	public void put(String name, Object obj) {
		session.setAttribute(name, obj);
	}
	
	/**
	 * Remove a registered object
	 * @param name {@link String} - Name
	 */
	public void remove(String name) {
		session.removeAttribute(name);
	}
		
	/**
	 * Retrieve an object from this context
	 * @param name {@link String} - Name of the object
	 * @return {@link Object} Null if not found
	 */
	public Object get(String name) {
		return session.getAttribute(name);
	}
	
	/**
	 * Redirect the appropriate view
	 * @param controllerName {@link String} - Controller
	 * @param actionName {@link String} - Action to find a view for
	 * @throws Exception 
	 */
	public void redirectToView(String controllerName, String actionName) throws Exception {
		String viewRoot = Config.get("controller", "view_path");
		String viewPath = viewRoot + "/" + controllerName + "/" + actionName + ".jsp";
		RequestDispatcher dispatcher = request.getRequestDispatcher(viewPath);
		if (dispatcher != null) {
			try {
				dispatcher.forward(request, response);
			} catch (ServletException e) {
				throw new Exception("Error while rendering view for " + actionName, e);
			} catch (IOException e) {
				throw new Exception("No view found for " + actionName);
			}
		} else {
			throw new Exception("Could not get dispatcher");
		}
	}
	
	/**
	 * Redirect to the 404 page
	 * @param msg {@link String} - Error message
	 * @throws Exception
	 */
	public void redirectTo404(String msg) throws Exception {
		response.sendError(404, msg);
	}
	
	/**
	 * Redirect the appropriate view
	 * @param actionName {@link String} - Action to find a view for
	 * @throws Exception 
	 */
	public void redirect(String path) throws Exception {
		RequestDispatcher dispatcher = request.getRequestDispatcher(path);
		if (dispatcher != null) {
			try {
				dispatcher.forward(request, response);
			} catch (Exception e) {
				throw new Exception("Error while redirecting to " + path);
			}
		} else {
			throw new Exception("Could not get dispatcher");
		}
	}
	
	/**
	 * Get the application cookie
	 * @return {@link Cookie}
	 */
	private Cookie getAppCookie() {
		String name = Config.get("application", "name");
		Cookie[] cookies = request.getCookies();
		Cookie c = null;
		if (cookies != null && cookies.length > 0) {
			for (Cookie cookie:cookies) {
				if (name.equals(cookie.getName())) {
					c = cookie;
					break;
				}
			}
			if (c == null) {
				c = new Cookie(name, "");
				c.setMaxAge(COOKIE_MAX_AGE);
				c.setPath(COOKIE_PATH);
			}
		}
		return c;
	}
	
	/**
	 * Add a cookie value to the response
	 * @param key {@link String} - Cookie name
	 * @param value {@link String} - Cookie value
	 */
	public void addCookie(String key, String value) {
		Cookie cookie = getAppCookie();
		String v = cookie.getValue();
		cookie.setValue(v + (!"".equals("") ? "|" : "") + key + ":" + value);
		cookie.setMaxAge(COOKIE_MAX_AGE);
		cookie.setPath(COOKIE_PATH);
		Logger.debug("Added cookie, new value = " + cookie.getValue());
		response.addCookie(cookie);
	}
	
	/**
	 * Retrieve a cookie value
	 * @param key {@link String} - Cookie name
	 * @return {@link String} Value or null if not found
	 */
	public String getCookie(String key) {
		Cookie cookie = getAppCookie();
		if (cookie != null) {
			String[] pairs = cookie.getValue().split("\\|");
			for (String pair:pairs) {
				String[] p = pair.split("\\:");
				if (p[0].equals(key)) {
					return p[1];
				}
			}
		}
		return null;
	}
	
	/**
	 * Remove a cookie value
	 * @param key {@link String} - Cookie name
	 */
	public void removeCookie(String key) {
		Cookie cookie = getAppCookie();
		String[] pairs = cookie.getValue().split("\\|");
		StringBuffer newValue = new StringBuffer();
		for (String pair:pairs) {
			if (!pair.startsWith(key + ":")) {
				if (newValue.length() > 0) {
					newValue.append("|");
				}
				newValue.append(pair);
			}
		}
		cookie.setValue(newValue.toString());
		cookie.setMaxAge(COOKIE_MAX_AGE);
		cookie.setPath(COOKIE_PATH);
		Logger.debug("Removed cookie, new value = " + cookie.getValue());
		response.addCookie(cookie);
	}
		
	/**
	 * Return whether the response already has a mime type
	 * @return boolean
	 */
	public boolean responseHasMimeType() {
		return (response.getContentType() != null);
	}
	
	/**
	 * Set the response type to JavaScript
	 */
	public void asJavascript() {
		response.setContentType("text/javascript");
	}

	/**
	 * Set the response type to JSONP
	 */
	public void asJSONP() {
		response.setContentType("application/x-javascript");
	}
	
	/**
	 * Set the response type to HTML
	 */
	public void asHTML() {
		response.setContentType("text/html");
	}
	
	/**
	 * Set the response type to JSON
	 */
	public void asJSON() {
		response.setContentType("application/json");
	}
	
	/**
	 * Set the response type to default
	 */
	public void asDefaultType() {
		response.setContentType(defaultMimeType);
	}
	
	/**
	 * Get the list of file items contained in the current request
	 * @return {@link List}<{@link FileItem}>
	 * @throws FileUploadException 
	 */
	@SuppressWarnings("unchecked")
	public List<FileItem> getFileItems() throws FileUploadException {
		FileItemFactory factory = new DefaultFileItemFactory();
		FileUpload upload = new FileUpload(factory);
		return upload.parseRequest(request);
	}
	
	/**
	 * Get an array parameter
	 * @param name {@link String} - Parameter name
	 * @return {@link String}[] Null if not found
	 */
	@SuppressWarnings("unchecked")
	public String[] getParameterAsArray(String name) {
		Enumeration<String> names = request.getParameterNames();
		Map<Integer, String> map = new HashMap<Integer, String>(0);
		String prefix = name + "[";
		int p, index, maxIndex = -1, l = prefix.length();
		while (names.hasMoreElements()) {
			String n = names.nextElement();
			if (n.startsWith(prefix)) {
				p = n.indexOf("]", l);
				index = l != p ? Integer.parseInt(n.substring(l, p)) : 0;
				if (index > maxIndex) {
					maxIndex = index;
				}
				map.put(index, request.getParameter(n));
			}
		}
		if (maxIndex >= 0) {
			String[] array = new String[maxIndex + 1];
			for (int k:map.keySet()) {
				array[k] = map.get(k);
			}
			map.clear();
			return array;
		}
		return null;
	}
	
	/**
	 * Get a map parameter
	 * @param name {@link String} - Parameter name
	 * @return {@link Map}<{@link String},{@link String}> Null if not found
	 */
	@SuppressWarnings("unchecked")
	public Map<String,String> getParameterAsMap(String name) {
		Enumeration<String> names = request.getParameterNames();
		Map<String, String> map = new HashMap<String, String>(0);
		String prefix = name + "[";
		String key;
		int p, l = prefix.length();
		while (names.hasMoreElements()) {
			String n = names.nextElement();
			if (n.startsWith(prefix)) {
				p = n.indexOf("]", l);
				key = n.substring(l, p);
				map.put(key, request.getParameter(n));
			}
		}
		return map.size() > 0 ? map : null;
	}
	
}
