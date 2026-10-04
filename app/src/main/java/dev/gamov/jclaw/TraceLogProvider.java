package dev.gamov.jclaw;

import java.util.function.Consumer;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/** Routes MCP subprocess stderr into the application trace, with HTTP debug disabled. */
public final class TraceLogProvider implements SLF4JServiceProvider {
  private static volatile Consumer<String> sink = System.err::println;

  public static void setSink(Consumer<String> value) {
    sink = value;
  }

  @Override
  public ILoggerFactory getLoggerFactory() {
    return TraceLogger::new;
  }

  @Override
  public IMarkerFactory getMarkerFactory() {
    return new BasicMarkerFactory();
  }

  @Override
  public MDCAdapter getMDCAdapter() {
    return new BasicMDCAdapter();
  }

  @Override
  public String getRequestedApiVersion() {
    return "2.0.99";
  }

  @Override
  public void initialize() {}

  private static final class TraceLogger extends AbstractLogger {
    private static final long serialVersionUID = 1L;

    TraceLogger(String loggerName) {
      name = loggerName;
    }

    @Override
    public boolean isTraceEnabled() {
      return false;
    }

    @Override
    public boolean isDebugEnabled() {
      return name.contains("ProcessStderrHandler");
    }

    @Override
    public boolean isInfoEnabled() {
      return true;
    }

    @Override
    public boolean isWarnEnabled() {
      return true;
    }

    @Override
    public boolean isErrorEnabled() {
      return true;
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
      return isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
      return isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
      return isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
      return isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
      return isErrorEnabled();
    }

    @Override
    protected String getFullyQualifiedCallerName() {
      return TraceLogger.class.getName();
    }

    @Override
    protected void handleNormalizedLoggingCall(
        Level level, Marker marker, String message, Object[] arguments, Throwable error) {
      // Provider diagnostics can contain secrets; expose only actual MCP subprocess trace lines.
      if (name.contains("ProcessStderrHandler")) {
        sink.accept("MCP STDERR " + MessageFormatter.arrayFormat(message, arguments).getMessage());
      }
    }
  }
}
