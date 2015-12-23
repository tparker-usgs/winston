/**
 * I waive copyright and related rights in the this work worldwide through the CC0 1.0 Universal
 * public domain dedication. https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.winston.server.httpCmd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.TimeZone;

import gov.usgs.net.HttpRequest;
import gov.usgs.net.NetTools;
import gov.usgs.plot.HelicorderSettings;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.data.HelicorderData;
import gov.usgs.volcanoes.core.CodeTimer;
import gov.usgs.volcanoes.core.time.Ew;
import gov.usgs.volcanoes.core.time.Time;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.Channel;
import gov.usgs.volcanoes.winston.db.Channels;
import gov.usgs.volcanoes.winston.db.WinstonDatabase;
import gov.usgs.volcanoes.winston.server.wwsCmd.WinstonConsumer;
import gov.usgs.volcanoes.winston.server.ConnectionStatistics;
import gov.usgs.volcanoes.winston.server.MalformedCommandException;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.util.AttributeKey;

/**
 * Return the wave server menu. Similar to earthworm getmenu command.
 *
 * @author Tom Parker
 *
 */
public final class HeliCommand extends HttpBaseCommand {

  private static final Logger LOGGER = LoggerFactory.getLogger(HeliCommand.class);

  public static final int MAX_HOURS = 144;
  public static final int MIN_HOURS = 1;
  public static final double MAX_TC = 21600;

  public HeliCommand() {
    super();
  }


  public void doCommand(ChannelHandlerContext ctx, FullHttpRequest request) throws UtilException {
    StringBuffer error = new StringBuffer();

    Map<String, String> params;
    try {
      params = getUnaryParams(request);
    } catch (UnsupportedMethodException e) {
      // TODO return 501 error
      e.printStackTrace();
      return;
    }

    /*
        final CodeTimer ct = new CodeTimer("HttpHeliCommand");

    String error = "";
    final HelicorderSettings settings = new HelicorderSettings();

    settings.channel = arguments.get("code");
    if (settings.channel == null)
      error = "Error: you must specify a channel (code).";
    else {
      settings.channel = settings.channel.replace('_', '$');
      if (settings.channel.indexOf(";") != -1)
        error = "Error: illegal characters in channel (code).";
    }

    final String tz = StringUtils.stringToString(arguments.get("tz"), DEFAULT_TZ);
    settings.timeZone = TimeZone.getTimeZone(tz);

    settings.endTime = getEndTime(arguments.get("t2"));
    if (Double.isNaN(settings.endTime))
      error = error + "Error: could not parse end time (t2). Should be " + INPUT_DATE_FORMAT + ".";

    settings.startTime = getStartTime(arguments.get("t1"), settings.endTime, ONE_HOUR);
    if (Double.isNaN(settings.startTime))
      error += "Error: cannot parse start time. Should be " + INPUT_DATE_FORMAT
          + " or -HH. I received " + arguments.get("t1");

    if (settings.endTime - settings.startTime > MAX_HOURS * ONE_HOUR)
      error += "Error: Plot must not be more that " + MAX_HOURS + " hours long";
    else if (settings.endTime - settings.startTime < MIN_HOURS * ONE_HOUR)
      error += "Error: Plot cannot be less than " + MIN_HOURS + " hour long";

    settings.timeChunk = StringUtils.stringToDouble(arguments.get("tc"), DEFAULT_TC) * ONE_MINUTE;
    if (settings.timeChunk <= 0 || settings.timeChunk > MAX_TC)
      error = error + "Error: time chunk (tc) must be greater than 0 and less than " + MAX_TC + ".";

    final int width = StringUtils.stringToInt(arguments.get("w"), DEFAULT_W);
    final int height = StringUtils.stringToInt(arguments.get("h"), DEFAULT_H);
    settings.setSizeFromPlotSize(width, height);

    if (settings.height * settings.width <= 0
        || settings.height * settings.width > wws.httpMaxSize())
      error = error + "Error: product of width (w) and height (h) must be between 1 and "
          + wws.httpMaxSize() + ".";

    settings.showClip = StringUtils.stringToBoolean(arguments.get("sc"), DEFAULT_SC);
    settings.forceCenter = StringUtils.stringToBoolean(arguments.get("fc"), DEFAULT_FC);
    settings.barRange = StringUtils.stringToInt(arguments.get("br"), -1);
    settings.clipValue = StringUtils.stringToInt(arguments.get("cv"), -1);

    settings.largeChannelDisplay = StringUtils.stringToBoolean(arguments.get("lb"));

    settings.minimumAxis = StringUtils.stringToBoolean(arguments.get("min"));
    if (settings.minimumAxis)
      settings.setMinimumSizes();

    if (error.length() > 0) {
      ct.stop();
      writeSimpleHTML(error);
    } else {
      HelicorderData heliData = null;
      try {
        heliData =
            data.getHelicorderData(settings.channel, settings.startTime, settings.endTime, 0);
      } catch (final UtilException e) {
      }
      ct.stop();

      // Did it take too long to gather the data?
      if (wws.getSlowCommandTime() > 0 && ct.getRunTimeMillis() > wws.getSlowCommandTime() * .75)
        wws.log(Level.INFO,
            String.format(
                "slow db query (%1.2f ms) http/heli " + settings.channel + " " + settings.startTime
                    + " -> " + settings.endTime + " ("
                    + decimalFormat.format(settings.endTime - settings.startTime) + ") ",
                ct.getRunTimeMillis()),
            socketChannel);

      if (heliData == null || heliData.rows() <= 0)
        writeSimpleHTML("Error: could not get helicorder data, check channel (code).");
      else {
        ct.start();
        final HttpResponse response = new HttpResponse("image/png");
        response.setVersion(request.getVersion());
        if (wws.httpRefreshInterval() > 0)
          response.setHeader("Refresh:",
              wws.httpRefreshInterval() + "; url=" + request.getResource());

        byte[] png;
        try {
          png = settings.createPlot(heliData).getPNGBytes();
          response.setLength(png.length);
          netTools.writeString(response.getHeaderString(), socketChannel);
          netTools.writeByteBuffer(ByteBuffer.wrap(png), socketChannel);
        } catch (final PlotException e) {
          e.printStackTrace();
          error = error + "Error: Can not create plot.";
        }

        ct.stop();
        // Did it take too long to deliver the data?
        if (wws.getSlowCommandTime() > 0 && ct.getRunTimeMillis() > wws.getSlowCommandTime() * .75)
          wws.log(Level.INFO,
              String.format(
                  "slow network (%1.2f ms) http/heli? " + settings.channel + " "
                      + settings.startTime + " -> " + settings.endTime + " ("
                      + decimalFormat.format(settings.endTime - settings.startTime) + ") ",
                  ct.getRunTimeMillis()),
              socketChannel);

      }
     */
    // validate input. Write error and return if bad.
    final int sortCol = StringUtils.stringToInt(params.get("ob"), HttpConstants.ORDER_BY);
    if (sortCol < 1 || sortCol > 8) {
      error.append("Error: could not parse ob = " + params.get("ob") + "<br>");
    }

    final String o = StringUtils.stringToString(params.get("so"), HttpConstants.SORT_ORDER);
    final char order = o.charAt(0);
    if (order != 'a' && order != 'd') {
      error.append("Error: could not parse so = " + params.get("so") + "<br>");
      return;
    }

    final String tz = StringUtils.stringToString(params.get("tz"), DEFAULT_TZ);
    final TimeZone timeZone = TimeZone.getTimeZone(tz);

    if (error.length() > 0) {
      HttpResponse response =
          new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.BAD_REQUEST);
      response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");

      boolean keepAlive = HttpHeaders.isKeepAlive(request);

      if (keepAlive) {
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, error.length());
        response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      }
      ctx.writeAndFlush(error);
    } else {

      String html = prepareResponse(sortCol, order, timeZone);

      if (html != null) {
        FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(),
            HttpResponseStatus.OK, Unpooled.copiedBuffer(html, Charset.forName("UTF-8")));
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, html.length());
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");

        if (HttpHeaders.isKeepAlive(request)) {
          response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        ctx.write(response);
      } else {
        LOGGER.error("NULL server menu.");
      }
    }
  }


  private String prepareResponse(int sortCol, char order, TimeZone timeZone) throws UtilException {
    // write header
    final String[] colTitle = {null, "Pin", "S", "C", "N", "L", "Earliest", "Most Recent", "Type"};
    colTitle[sortCol] += order == 'a' ? " &#9652;" : " &#9662;";

    final char[] colOrd = new char[colTitle.length];
    Arrays.fill(colOrd, 'a');
    if (order == 'a')
      colOrd[sortCol] = 'd';

    final StringBuilder output = new StringBuilder();

    output.append("<HTML><HEAD><TITLE>Winston Server Menu</TITLE></HEAD><BODY>");

    output.append("<table CELLPADDING=\"5\"><tr>");
    for (int i = 1; i < colTitle.length; i++)
      output.append(
          "<th><a href=\"?ob=" + i + "&so=" + colOrd[i] + "\">" + colTitle[i] + "</a></th>");

    output.append("</tr>");

    // get and sort menu
    List<Channel> channels;
    try {
      channels = databasePool.doCommand(new WinstonConsumer<List<Channel>>() {

        public List<Channel> execute(WinstonDatabase winston) throws UtilException {
          return new Channels(winston).getChannels();
        }

      });
    } catch (Exception e) {
      throw new UtilException(e.getMessage());
    }

    HeliCommand menuCmd = new HeliCommand();
    final List<String> list = gov.usgs.volcanoes.winston.server.wwsCmd.MenuCommand.generateMenu(channels, true);


    final String[][] menu = new String[list.size()][8];
    int i = 0;
    for (final String s : list)
      menu[i++] = s.split("\\s");

    Arrays.sort(menu, getMenuComparator(sortCol, order));

    // display menu items
    for (final String[] line : menu) {
      if (line.length < 8) {
        output.append("can't parse line, skipping. " + Arrays.toString(line));
        continue;
      }

      SimpleDateFormat dateF = new SimpleDateFormat(Time.STANDARD_TIME_FORMAT);
      dateF.setTimeZone(timeZone);

      final double start = Double.parseDouble(line[6]);
      final double end = Double.parseDouble(line[7]);

      output.append("<tr>");
      output.append("<td>" + line[1] + "</td>");
      output.append("<td>" + line[2] + "</td>");
      output.append("<td>" + line[3] + "</td>");
      output.append("<td>" + line[4] + "</td>");
      output.append("<td>" + line[5] + "</td>");
      output.append("<td>" + dateF.format(Ew.asEpoch(start)) + "</td>");
      output.append("<td>" + dateF.format(Ew.asEpoch(end)) + "</td>");
      output.append("<td>" + line[8] + "</td>");
      output.append("</tr>\n");
    }

    output.append("</table>");
    output.append("</BODY></HTML>");
    return output.toString();
  }


  private Comparator<String[]> getMenuComparator(final int sortCol, final char order) {
    return new Comparator<String[]>() {
      public int compare(final String[] e1, final String[] e2) {
        // numeric columns
        if (sortCol == 1 || sortCol == 6 || sortCol == 7) {
          final double d1 = Double.parseDouble(e1[sortCol]);
          final double d2 = Double.parseDouble(e2[sortCol]);

          // Do this the hard way to avoid an int overflow.
          // Yes, this bug was encountered in a running system.
          final double d = d1 - d2;
          if (d == 0)
            return 0;

          final int i = (d < 0 ? -1 : 1);

          if (order == 'a')
            return i;
          else
            return -i;
        }
        // textual columns
        else {
          if (order == 'a')
            return e1[sortCol].compareTo(e2[sortCol]);
          else
            return e2[sortCol].compareTo(e1[sortCol]);
        }
      }
    };
  }

  public String getUsage(final HttpRequest req) {
    final String Url = "http://" + req.getHeader("Host") + "/menu";

    final StringBuilder output = new StringBuilder();

    output.append(
        "<script>function buildMenuUrl() {" + "var urlDiv = document.getElementById(\"menuUrl\");\n"
            + "var menuOB = document.getElementById(\"menuOB\");\n"
            + "var menuSO = document.getElementById(\"menuSO\");\n"
            + "var menuTZ = document.getElementById(\"menuTZ\");\n"
            + "var a = document.createElement('a');\n" + "var linkUrl = \"http://"
            + req.getHeader("Host") + "/menu?\";\n");

    output.append(
        "if (menuOB.value != \"" + HttpConstants.ORDER_BY + "\") { linkUrl += \"&ob=\" + menuOB.value;}\n");
    output.append(
        "if (menuSO.value != \"" + HttpConstants.SORT_ORDER + "\") { linkUrl += \"&so=\" + menuSO.value;}\n");
    output.append(
        "if (menuTZ.value != \"" + DEFAULT_TZ + "\") { linkUrl += \"&tz=\" + menuTZ.value;}\n");
    output.append("linkUrl = linkUrl.replace(\"?&\", \"?\");\n");
    output.append("linkUrl = linkUrl.replace(/\\?$/, \"\");\n");
    output.append("a.href = linkUrl;\n" + "a.text = linkUrl;\n" + "a.textContent = linkUrl; \n"
        + "a.textContent = linkUrl; \n"
        + "while(urlDiv.hasChildNodes()) {urlDiv.removeChild(urlDiv.lastChild);}"
        + "urlDiv.appendChild(a);\n" + "}</script>\n");

    output.append(
        "Returns the server menu. The menu contains the list of stations in this winston along with the earliest and most recent data point for each station.\n");
    output.append("<div class=\"tabContentTitle\">URL Builder</DIV>\n");
    output.append("<div class=\"tabContent\">\n");
    output.append("<FORM>\n");
    output.append("<div class=\"left\"><div class=\"left\">\n");
    output.append("<label for=\"tz\">Time Zone</label><br>\n");
    output.append(
        "<select onchange=\"buildMenuUrl()\" class=\"timeZone\" id=\"menuTZ\" name=\"tz\" size=8>"
            + "</select>");
    output.append("</div><div class=\"right\">\n");
    output.append(
        "<br><div class=\"input\" style=\"width: 20em\"><label for=\"ob\">Order By</label>");
    output.append("<select id=\"menuOB\" onchange=\"buildMenuUrl()\" name=\"ob\">\n"
        + "<option value=1>Pin</option>" + "<option value=2 selected>Station</option>"
        + "<option value=3>Component</option>" + "<option value=4>Network</option>"
        + "<option value=5>Location</option>" + "<option value=6>Earliest</option>"
        + "<option value=7>Most Recent</option>" + "<option value=8>Type</Option>"
        + "</select></div><br>\n");
    output
        .append("<div class=\"input\" style=\"width: 20em\"><label for=\"so\">Sort Order</label>");
    output.append("<select id=\"menuSO\" onchange=\"buildMenuUrl()\" name=\"so\">"
        + "<option value=\"a\" selected>Ascending</option>"
        + "<option value=\"d\">Descending</option>" + "</select></div><br>");

    output.append("</div></div></FORM><div class=\"clear\"></div>\n");
    output.append("<HR class=\"urlBuilder\"><b>URL:</b><BR><div id=\"menuUrl\"></div>");
    output.append("</div>");
    output.append("<div class=\"tabContentTitle\">Arguments</DIV>\n");
    output.append("<div class=\"tabContent\">\n");
    output.append(
        "<code>ob</code>: <b>Order By</b> The column number used to order the menu (default = "
            + HttpConstants.ORDER_BY + ").<br><br>\n"
            + "<code>so</code>: <b>Sort Order</b> How to order the menu, a is ascending, d is decending (default = "
            + HttpConstants.SORT_ORDER + ").<br><br>\n" + "<code>tz</code>: <b>Time Zone</b> (deafult = "
            + DEFAULT_TZ + ")<br><br>\n");
    output.append("</div>");
    output.append("<div class=\"tabContentTitle\">Examples</DIV>\n");
    output.append("<div class=\"tabContent\">\n");
    output.append("Most recently added channels:<br><a href=\"http://" + req.getHeader("Host")
        + "/menu?ob=6&so=d\">" + "http://" + req.getHeader("Host")
        + "/menu?ob=6&so=d</a><br><p><br>");
    output.append("Channels with least recent data:<br><a href=\"http://" + req.getHeader("Host")
        + "/menu?ob=7&so=a\">" + "http://" + req.getHeader("Host") + "/menu?ob=7&so=a</a><p>");
    output.append("</div>");
    return output.toString();
  }

  public String getAnchor() {
    return "menu";
  }

  public String getTitle() {
    return "Server Menu";
  }

  @Override
  public String getCommand() {
    return "/menu";
  }
}
