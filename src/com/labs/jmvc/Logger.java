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

	private static final String defaultNamespace = Config.get("logging", "namespace");
	private static final String logFile = Config.get("logging", "log_dir") + "/" + Config.get("logging", "log_file");
	private static final String fileDatePattern = Config.get("logging", "file_pattern");
	private static final String recordPattern = Config.get("logging", "record_pattern");
	private static final String level = Config.get("logging", "level");
	private static org.apache.log4j.Logger logger;
	
	public static org.apache.log4j.Logger getLogger() {
		return getLogger(defaultNamespace, logFile);
	}
	
	public static org.apache.log4j.Logger getLogger(String namespace, String logFilePath) {
		if (logger == null) {
			PatternLayout layout = new PatternLayout(recordPattern);
			logger = org.apache.log4j.Logger.getLogger(namespace);
			logger.setLevel(Level.toLevel(level));
			/* Setup console logging */
			if (Config.getBool("logging", "stdout")) {
				ConsoleAppender C = new ConsoleAppender();
				C.setLayout(layout);
				C.setImmediateFlush(true);
				C.activateOptions();
				logger.addAppender(C);
			}
			/* Setup file logging */
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
		getLogger().log(Level.INFO, msg);
	}
	
	public static void debug(String msg) {
		getLogger().log(Level.DEBUG, msg);
	}
	
	public static void trace(String msg) {
		getLogger().log(Level.TRACE, msg);
	}
	
	public static void warn(String msg) {
		getLogger().log(Level.WARN, msg);
	}
	
	public static void error(String msg) {
		error(msg, null);
	}

	public static void error(String msg, Throwable e) {
		StringBuffer buf = new StringBuffer();
		buf.append("[" + UUID.randomUUID() + "] " + msg);
		if (e != null) {
			buf.append(": ");
			buf.append(e.getMessage());
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
