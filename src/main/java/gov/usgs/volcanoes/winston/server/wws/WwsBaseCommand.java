/**
 * I waive copyright and related rights in the this work worldwide through the CC0 1.0 Universal
 * public domain dedication. https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.winston.server.wws;

import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.server.BaseCommand;
import gov.usgs.volcanoes.winston.server.MalformedCommandException;
import io.netty.channel.ChannelHandlerContext;

/**
 *
 * @author Dan Cervelli
 */
abstract public class WwsBaseCommand extends BaseCommand implements WwsCommand {
  private static final Logger LOGGER = LoggerFactory.getLogger(WwsBaseCommand.class);

  protected final static int ONE_HOUR_S = 60 * 60;
  protected final static int ONE_DAY_S = 24 * ONE_HOUR_S;

  /**
   * Constructor.
   */
  public WwsBaseCommand() {
    super();
  }

  /**
   * Do the work. Return response to the client.
   * 
   * @param ctx my context
   * @param req the request
   * @throws MalformedCommandException when I cannot understand the command
   * @throws UtilException when things go wrong
   */
  public void respond(ChannelHandlerContext ctx, WwsCommandString req)
      throws MalformedCommandException, UtilException {
    InetSocketAddress remoteAddr = (InetSocketAddress) ctx.channel().remoteAddress();
    LOGGER.info("{} asks {}", remoteAddr.getAddress(), prettyRequest(req));
    try {
      doCommand(ctx, req);
    } catch (java.lang.OutOfMemoryError e) {
      ctx.writeAndFlush("FU\n");    
      LOGGER.error("Caught OutOfMemoryError fulfilling ({} asks {})", remoteAddr.getAddress(), prettyRequest(req));
    }
  }

  protected static DecimalFormat getDecimalFormat() throws UtilException {
    try {
      DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getInstance();
      decimalFormat.setMaximumFractionDigits(3);
      decimalFormat.setGroupingUsed(false);
      return decimalFormat;
    } catch (ClassCastException ex) {
      throw new UtilException("Unable to cast NumberFormat to DecimalFormat.");
    }
  }

  protected String prettyRequest(WwsCommandString req) {
    return req.commandString;
  }
}
