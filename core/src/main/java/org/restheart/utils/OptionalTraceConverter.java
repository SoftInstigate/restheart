package org.restheart.utils;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class OptionalTraceConverter extends ClassicConverter {
	@Override
	public String convert(ILoggingEvent event) {
		var traceId = event.getMDCPropertyMap().get("traceId");
		if (traceId == null || traceId.isEmpty()) {
			return "";
		}
		return String.format("[Trace:_%4s]_", traceId)
			.replace(' ', '0')
			.replace("_", " ");
	}
}