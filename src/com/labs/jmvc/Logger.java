package com.labs.jmvc;

import java.util.UUID;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;

/**
 * Logging factory
 * @author Benjamin Dezile
 */
public class Logger {

	private static String defaultNamespace;
	private static String logFile;
	private static String fileDatePattern;
	private static String recordPattern;
	private static String level;
	private static org.apache.log4j.Logger logger;
	private static boolean disabled = false;
	
	public static void disable() {
		disabled = true;
	}
	
	public static void enable() {
		disabled = false;
	}
	
	public static org.apache.log4j.Logger getLogger() {
		return getLogger(null, null);
	}
	
	public static org.apache.log4j.Logger getLogger(String namespace, String logFilePath) {
		if (disabled) {
			throw new IllegalStateException("Logging is disabled");
		}
		if (logger == null) {
			
			// Get config
			defaultNamespace = Config.get("logging", "namespace");
			logFile = Config.get("logging", "log_dir") + "/" + Config.get("logging", "log_file");
			fileDatePattern = Config.get("logging", "file_pattern");
			recordPattern = Config.get("logging", "record_pattern");
			level = Config.get("logging", "level");
			
			if (namespace == null) {
				namespace = defaultNamespace;
			}
			if (logFilePath == null) {
				logFilePath = logFile;
			}
			
			PatternLayout layout = new PatternLayout(recordPattern);
			logger = org.apache.log4j.Logger.getLogger(namespace);
			logger.setLevel(Level.toLevel(level));
			// Setup console logging
			if (Config.getBool("logging", "stdout")) {
				ConsoleAppender C = new ConsoleAppender();
				C.setLayout(layout);
				C.setImmediateFlush(true);
				C.activateOptions();
				logger.addAppender(C);
			}
			// Setup file logging
			DailyRollingFileAppender A = new DailyRollingFileAppender();
			A.setFile(logFilePath);
			A.setDatePattern(fileDatePattern);
			A.setLayout(layout);
			A.activateOptions();
			logger.addAppender(A);
		}
		return logger;
	}
	
	public static void info(String msg) {
		if (!disabled) {
			getLogger().log(Level.INFO, msg);
		}
	}
	
	public static void debug(String msg) {
		if (!disabled) {
			getLogger().log(Level.DEBUG, msg);
		}
	}
	
	public static void trace(String msg) {
		if (!disabled) {
			getLogger().log(Level.TRACE, msg);
		}
	}
	
	public static void warn(String msg) {
		if (!disabled) {
			getLogger().log(Level.WARN, msg);
		}
	}
	
	public static void error(String msg) {
		error(msg, null);
	}

	public static void error(String msg, Throwable e) {
		if (!disabled) {
			StringBuffer buf = new StringBuffer();
			buf.append("[" + UUID.randomUUID() + "] " + msg);
			if (e != null) {
				buf.append(": ");
				buf.append(e != null ? e.getMessage() : "No error object");
				buf.append("\n");
				for (StackTraceElement el:e.getStackTrace()) {
					buf.append("\tat ");
					buf.append(el.getClassName());
					buf.append(".");
					buf.append(el.getMethodName());
					buf.append("(");
					buf.append(el.getFileName());
					buf.append(":");
					buf.append(el.getLineNumber());
					buf.append(")\n");
				}
			}
			getLogger().log(Level.ERROR, buf.toString());
		}
	}
	
}
