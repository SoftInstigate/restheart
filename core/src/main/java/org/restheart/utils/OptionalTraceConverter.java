package org.restheart.utils;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class OptionalTraceConverter extends ClassicConverter {
	private static final String TRACE_ID_KEY = "traceId";
	
	@Override
	public String convert(ILoggingEvent event) {
		var mdcMap = event.getMDCPropertyMap();
		var traceId = mdcMap.get(TRACE_ID_KEY);
		
		if (traceId == null || traceId.isEmpty()) {
			return "";
		}
		
		// Fast path: if only traceId exists and it's auto-generated (4 chars or less), format as before
		if (mdcMap.size() == 1 && traceId.length() <= 4) {
			return formatTraceIdOnly(traceId);
		}
		
		// Build trace info with tracing headers only (exclude internal traceId)
		StringBuilder traceInfo = new StringBuilder(64);
		
		for (var entry : mdcMap.entrySet()) {
			var key = entry.getKey();
			if (!TRACE_ID_KEY.equals(key)) {
				var value = entry.getValue();
				if (value != null && !value.isEmpty()) {
					if (traceInfo.length() > 0) {
						traceInfo.append(", ");
					}
					traceInfo.append(key).append(": ").append(value);
				}
			}
		}
		
		// If we have tracing headers, show them without "Trace:" prefix
		// Otherwise fall back to showing the traceId
		if (traceInfo.length() > 0) {
			return "[" + traceInfo + "] ";
		} else {
			return formatTraceIdOnly(traceId);
		}
	}
	
	private String formatTraceIdOnly(String traceId) {
		// Optimize for common case where traceId is already 4 chars
		if (traceId.length() == 4) {
			return "[trace-id: " + traceId + "] ";
		}
		// Pad with zeros if needed
		return String.format("[trace-id:_%4s]_", traceId)
			.replace(' ', '0')
			.replace("_", " ");
	}
}
