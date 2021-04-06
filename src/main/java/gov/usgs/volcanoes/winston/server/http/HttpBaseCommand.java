/**
 * I waive copyright and related rights in the this work worldwide
 * through the CC0 1.0 Universal public domain dedication.
 * https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.winston.server.http;

import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.volcanoes.core.configfile.ConfigFile;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.server.BaseCommand;
import gov.usgs.volcanoes.winston.server.MalformedCommandException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * All possible HTTP commands are expected to extend this class. When a new class is created, it
 * must be added to HttpGetCommand
 *
 * @author Tom Parker
 *
 */
public abstract class HttpBaseCommand extends BaseCommand implements HttpCommand {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpBaseCommand.class);


  protected ConfigFile configFile;

  /**
   * Constructor.
   */
  public HttpBaseCommand() {
    super();
  }


  /**
   * Do the work. Return response to the browser.
   * 
   * @param ctx handler context
   * @param request my request
   * @throws MalformedCommandException when cannot parse command
   * @throws UtilException when things go wrong
   */
  public void respond(ChannelHandlerContext ctx, FullHttpRequest request)
      throws MalformedCommandException, UtilException {
    InetSocketAddress remoteAddr = (InetSocketAddress) ctx.channel().remoteAddress();
    LOGGER.debug("{} asks {}", remoteAddr.getAddress(), request.getUri());
    try {
      doCommand(ctx, request);
    } catch (java.lang.OutOfMemoryError e) {
      HttpResponse response =
          new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.SERVICE_UNAVAILABLE);
      response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");

      String message = "Caught OutOfMemoryError";
      boolean keepAlive = HttpHeaders.isKeepAlive(request);

      if (keepAlive) {
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, message.length());
        response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      }
      ctx.writeAndFlush(message);

      LOGGER.error("Caught OutOfMemoryError fulfilling ({} asks {})", remoteAddr.getAddress(), request.getUri());
    }

  }

  protected Map<String, List<String>> getParams(FullHttpRequest request)
      throws UnsupportedMethodException {
    Map<String, List<String>> params = null;
    HttpMethod method = request.getMethod();

    if (method == HttpMethod.GET) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
      params = queryStringDecoder.parameters();
      // } else if (method == HttpMethod.POST) {
      // TODO: support post
      // HttpPostRequestDecoder queryStringDecoder = new HttpPostRequestDecoder(request);
      // Map<String, List<String>> params = queryStringDecoder.;
    } else {
      throw new UnsupportedMethodException("Unsupported HTTP method " + method);
    }

    return params;
  }

  protected Map<String, String> getUnaryParams(FullHttpRequest request)
      throws UnsupportedMethodException {
    Map<String, String> params = new HashMap<String, String>();
    HttpMethod method = request.getMethod();

    if (method == HttpMethod.GET) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
      for (String param : queryStringDecoder.parameters().keySet()) {
        params.put(param, queryStringDecoder.parameters().get(param).get(0));
      }
      // } else if (method == HttpMethod.POST) {
      // TODO: support post
      // HttpPostRequestDecoder queryStringDecoder = new HttpPostRequestDecoder(request);
      // Map<String, List<String>> params = queryStringDecoder.;
    } else {
      throw new UnsupportedMethodException("Unsupported HTTP method " + method);
    }

    return params;
  }

  /**
   * 
   * @param t1 start time String
   * @param endTime end time
   * @param mult number of seconds per interval used for relative times. 60 for minutes, 60*60 for
   *          hours, 60*60*24 for days
   * @return
   * @throws ParseException
   */
  protected Double getStartTime(final String t1, final Double endTime, final long mult) {
    Double startTime = Double.NaN;

    if (t1 == null || t1.substring(0, 1).equals("-")) {
      final double hrs = StringUtils.stringToDouble(t1, -12);
      startTime = endTime + hrs * mult;
    } else {
      final DateFormat dateFormat = new SimpleDateFormat(HttpConstants.INPUT_DATE_FORMAT);
      dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
      Date bt;
      try {
        bt = dateFormat.parse(t1);
        startTime = J2kSec.fromDate(bt);
      } catch (final ParseException e) {
        startTime = Double.NaN;
      }
    }

    return startTime;
  }

  /*
   * convert time back to UTC
   */
  protected Double getStartTime(final String t1, final Double endTime, final long mult,
      final TimeZone tz) {
    final double startTime = getStartTime(t1, endTime, mult);
    return startTime - tz.getOffset(J2kSec.asEpoch(endTime));
  }

  /**
   * parse end time
   * 
   * @param t2
   * @return
   */
  protected Double getEndTime(final String t2) {
    Double endTime = Double.NaN;
    if (t2 == null || t2.equalsIgnoreCase("now"))
      endTime = J2kSec.now();
    else {
      final DateFormat dateFormat = new SimpleDateFormat(HttpConstants.INPUT_DATE_FORMAT);
      dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
      Date bt;
      try {
        bt = dateFormat.parse(t2);
        endTime = J2kSec.fromDate(bt) - 1;
      } catch (final ParseException e) {
        endTime = Double.NaN;
      }
    }

    return endTime;
  }

  /**
   * convert back to UTC
   * 
   */
  protected Double getEndTime(final String t2, final TimeZone tz) {
    final Double endTime = getEndTime(t2);
    return endTime - tz.getOffset(J2kSec.asEpoch(endTime));
  }


  /**
   * Convert a boolean value to an integer as passed in arguments.
   * 
   * @param in
   * @return
   */
  protected int boolToInt(final boolean in) {
    return in == true ? 1 : 0;
  }


  /**
   * Configfile mutator.
   * 
   * @param configFile my new config file
   */
  public void setConfig(ConfigFile configFile) {
    this.configFile = configFile;
  }
}
