/**
 * I waive copyright and related rights in the this work worldwide through the CC0 1.0 Universal
 * public domain dedication. https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.winston.server.wwsCmd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import gov.usgs.math.DownsamplingType;
import gov.usgs.net.ConnectionStatistics;
import gov.usgs.net.NetTools;
import gov.usgs.plot.data.HelicorderData;
import gov.usgs.plot.data.RSAMData;
import gov.usgs.volcanoes.core.Zip;
import gov.usgs.volcanoes.core.time.CurrentTime;
import gov.usgs.volcanoes.core.time.Ew;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.Channel;
import gov.usgs.volcanoes.winston.db.Channels;
import gov.usgs.volcanoes.winston.db.Data;
import gov.usgs.volcanoes.winston.db.WinstonDatabase;
import gov.usgs.volcanoes.winston.legacyServer.WWS;
import gov.usgs.volcanoes.winston.legacyServer.WWSCommandString;
import gov.usgs.volcanoes.winston.legacyServer.cmd.BaseCommand;
import io.netty.channel.ChannelHandlerContext;

/**
 * Return Channel details.
 * 
 * @author Dan Cervelli
 * @author Tom Parker
 */
public class GetScnlRsamRawCommand extends WwsBaseCommand {
  private static final Logger LOGGER = LoggerFactory.getLogger(GetScnlRsamRawCommand.class);

  public GetScnlRsamRawCommand() {
    super();
  }

  public void doCommand(ChannelHandlerContext ctx, WwsCommandString cmd)
      throws MalformedCommandException, UtilException {

    if (!cmd.isLegalSCNLTT(10) || Double.isNaN(cmd.getDouble(8))
        || cmd.getInt(9) == Integer.MIN_VALUE) {
      throw new MalformedCommandException();
    }
    final double t1 = cmd.getT1(true);
    final double t2 = cmd.getT2(true);
    final int ds = (int) cmd.getDouble(8);
    final String scnl = cmd.getWinstonSCNL();
    final DownsamplingType dst = (ds < 2) ? DownsamplingType.NONE : DownsamplingType.MEAN;

    RSAMData rsam;
    try {
      rsam = databasePool.doCommand(new WinstonConsumer<RSAMData>() {
        public RSAMData execute(WinstonDatabase winston) throws UtilException {
          return new Data(winston).getRSAMData(scnl, t1, t2, 0, dst, ds);
        }

      });
    } catch (Exception e) {
      throw new UtilException(e.getMessage());
    }

    ByteBuffer bb = null;
    if (rsam != null && rsam.rows() > 0)
      bb = (ByteBuffer) rsam.toBinary().flip();
    
    if (cmd.getInt(9) == 1)
      bb = ByteBuffer.wrap(Zip.compress(bb.array()));

    LOGGER.warn("returning {} rsam bytes", bb.limit());
    ctx.write(cmd.getID() + " " + bb.limit() + '\n');
    ctx.writeAndFlush(bb.array());
  }
}