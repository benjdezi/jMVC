package com.labs.jmvc;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Abstract controller
 * @author Benjamin Dezile
 */
public abstract class Controller extends HttpServlet {

	private static final long serialVersionUID = -6205350259500257015L;
	private static final Class<?>[] actionSignature = new Class<?>[]{ Context.class };
	private static final String actionPrefix = "execute";
	private Map<String, Method> actions; 
	private String name;

	/**
	 * Create a new controller
	 * @param name {@link String} - Associated name
	 */
	public Controller(String name) {
		super();
		this.name = name;
		this.actions = new HashMap<String, Method>(0);
		Class<?>[] paramTypes;
		for (Method meth:this.getClass().getMethods()) {
			if (meth.getName().startsWith(actionPrefix)) {
				paramTypes = meth.getParameterTypes();
				for (int i=0;i<paramTypes.length;i++) {
					if (!paramTypes[i].equals(actionSignature[i]) || (paramTypes[i].getSuperclass() != null && !paramTypes[i].getSuperclass().equals(actionSignature[i]))) {
						throw new RuntimeException("Invalid action signature for " + meth.getName() + " in " + name + " controller");	
					}
				}
				actions.put(meth.getName().toLowerCase(), meth);
			}
		}
		Logger.debug("Created new controller: " + name + " with " + actions);
	}
	
	/**
	 * Handle post requests
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	/**
	 * Handle get requests
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String actionName = getActionName(request);
		Method action = findActionMethod(actionName);
		Context context = new Context(request, response); 
		if (action != null) {
			try {
				/* Execute the action */
				Logger.debug("Calling action: " + name + "->" + actionName);
				Object respData = action.invoke(this, context);
				if (!response.isCommitted()) {
					if (respData == null) {
						/* Try to redirect to view */
						String viewName = actionName.replace(actionPrefix, "").toLowerCase();
						Logger.debug("Redirecting to view: " + viewName);
						context.redirectToView(name, viewName);
					} else {
						/* Return response data */
						setResponseType(context, respData);
						Logger.debug("Response from " + name + "->" + actionName + ": (" + response.getContentType() + ") " + respData);
						PrintWriter out = response.getWriter();
						out.write(respData.toString());
						out.flush();
						out.close();
						return;
					}
				}
				return;
			} catch (Exception e) {
				/* Error */
				if (e instanceof InvocationTargetException) {
					Throwable t = ((InvocationTargetException) e).getTargetException();
					if (t instanceof Exception) {
						e = (Exception)((InvocationTargetException)e).getTargetException();
					} else {
						e = new Exception(t);
					}
				}
				Logger.error("Controller exception", e);
				response.reset();
				response.sendError(500, "Error while calling " + name + "->" + actionName + ": " + e.getMessage());
				return;
			}
		}
		/* Action not found */
		Logger.warn("Action not found: " + actionName);
		response.sendError(404, actionName != null ? "Action not found: " + actionName : "No action");
	}
		
	/**
	 * Return the controller's name
	 * @return {@link String}
	 */
	protected String getName() {
		return name;
	}
	
	/**
	 * Get the name of the current action
	 * @param request {@link HttpServletRequest} - Request
	 * @return {@link String}
	 */
	private static String getActionName(HttpServletRequest request) {
		String pathInfo = request.getPathInfo();
		if (pathInfo != null) {
			StringBuffer buf = new StringBuffer(pathInfo);
			if (buf.indexOf("/") == 0) {
				return buf.substring(1);
			}
			return buf.toString();
		}
		return null;
	}
	
	/**
	 * Find a given action method
	 * @param name {@link String} - Action name
	 * @return {@link Method}
	 */
	private Method findActionMethod(String name) {
		if (name != null) {
			return actions.get(actionPrefix + name.toLowerCase());
		} 
		return null;
	}
		
	/**
	 * Try to get the response type if not already set
	 * @param context {@link Context} - Current controller context
	 * @param responseData {@link Object} - Reponse data
	 */
	private void setResponseType(Context context, Object responseData) {
		if (context.responseHasMimeType()) {
			/* Already set, we're done here */
			return;
		}
		if (responseData == null) {
			/* No data */
			return;
		}
		String data = responseData.toString().trim();
		if (responseData instanceof JSONObject || 
			responseData instanceof JSONArray || 
			(data.startsWith("{") && data.endsWith("}")) || 
			(data.startsWith("[") && data.endsWith("]"))) {
			/* JSON */
			context.asJSON();
		} else if (data.startsWith("<script") && data.endsWith("</script>")) {
			/* JavaScript */
			context.asJavascript();
		} else if (data.startsWith("<") && data.endsWith(">")) {
			/* HMTL */
			context.asHTML();
		} else {
			/* Default */
			context.asDefaultType();
		}
	}
	
	/**
	 * Controller exception
	 * @author Benjamin Dezile
	 */
	protected static class ControllerException extends ServletException {
		private static final long serialVersionUID = 8778969372227983302L;
		public ControllerException(Exception e) {
			super(e);
		}
		public ControllerException(String message) {
			super(message);
		}
	}
		
}
