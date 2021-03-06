/**
 * I waive copyright and related rights in the this work worldwide
 * through the CC0 1.0 Universal public domain dedication.
 * https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.winston.server.wws.cmd;

import gov.usgs.volcanoes.core.data.Scnl;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.db.Channels;
import gov.usgs.volcanoes.winston.db.Data;
import gov.usgs.volcanoes.winston.db.WinstonDatabase;
import gov.usgs.volcanoes.winston.server.WinstonConsumer;
import gov.usgs.volcanoes.winston.server.wws.WwsBaseCommand;

/**
 * Abstract request for data using an Earthworm WSV-style command
 * 
 * @author Tom Parker
 *
 */
public abstract class EwDataRequest extends WwsBaseCommand {
  protected Integer getChanId(final Scnl scnl) throws UtilException {
    final Integer chanId;
    try {
      chanId = databasePool.doCommand(new WinstonConsumer<Integer>() {
        public Integer execute(WinstonDatabase winston) throws UtilException {
          return new Channels(winston).getChannelID(scnl);
        }
      });
    } catch (Exception e) {
      throw new UtilException(String.format("Unable to get chanId for %s. (%s)", scnl,e.getMessage()));
    }
    return chanId;
  }

  protected double[] getTimeSpan(final Integer chanId) throws UtilException {
    double[] timeSpan;
    try {
      timeSpan = databasePool.doCommand(new WinstonConsumer<double[]>() {
        public double[] execute(WinstonDatabase winston) throws UtilException {
          return new Data(winston).getTimeSpan(chanId);
        }
      });
    } catch (Exception e) {
      throw new UtilException("Unable to get timeSpan.");
    }
    return timeSpan;
  }
}