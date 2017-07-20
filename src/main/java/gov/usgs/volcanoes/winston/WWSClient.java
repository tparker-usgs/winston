package gov.usgs.volcanoes.winston;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.earthworm.WaveServer;
import gov.usgs.net.ReadListener;
import gov.usgs.plot.data.HelicorderData;
import gov.usgs.plot.data.RSAMData;
import gov.usgs.plot.data.Wave;
import gov.usgs.plot.data.file.FileType;
import gov.usgs.plot.data.file.SeismicDataFile;
import gov.usgs.volcanoes.core.Zip;
import gov.usgs.volcanoes.core.data.Scnl;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.TimeSpan;
import gov.usgs.volcanoes.core.util.Retriable;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.client.MenuHandler;
import gov.usgs.volcanoes.winston.client.WWSClientArgs;
import gov.usgs.volcanoes.winston.client.WWSClientHandler;
import gov.usgs.volcanoes.winston.client.WWSCommandHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.AttributeKey;

/**
 * A class that extends the Earthworm Wave Server to include a get helicorder
 * function for WWS.
 *
 * @author Dan Cervelli
 */
public class WWSClient extends WaveServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(WWSClient.class);
	private static final int DEFAULT_WWS_PORT = 16022;

	protected ReadListener readListener;
	private final String server;
	private final int port;

	public WWSClient(final String server, final int port) {
		super(server, port);
		setTimeout(60000);
		//
		this.server = server;
		this.port = port;
	}

	public void setReadListener(final ReadListener rl) {
		readListener = rl;
	}

	public int getProtocolVersion() {
		int version = 1;
		try {
			if (!connected())
				connect();

			socket.setSoTimeout(1000);
			writeString("VERSION\n");
			final String result = readString();
			version = Integer.parseInt(result.split(" ")[1]);
		} catch (final Exception e) {
		} finally {
			try {
				socket.setSoTimeout(timeout);
			} catch (final Exception e) {
			}
		}
		return version;
	}

	protected byte[] getData(final String req, final boolean compressed) {
		byte[] ret = null;
		final Retriable<byte[]> rt = new Retriable<byte[]>("WWSClient.getData()", maxRetries) {
			@Override
			public void attemptFix() {
				close();
			}

			@Override
			public boolean attempt() throws UtilException {
				try {
					if (!connected())
						connect();

					writeString(req);
					final String info = readString();
					if (info.startsWith("ERROR")) {
						logger.warning("Sent: " + req);
						logger.warning("Got: " + info);
						return false;
					}

					final String[] ss = info.split(" ");
					final int bytes = Integer.parseInt(ss[1]);
					if (bytes == 0)
						return true;

					byte[] buf = readBinary(bytes, readListener);
					if (compressed)
						buf = Zip.decompress(buf);

					result = buf;
					return true;
				} catch (final SocketTimeoutException e) {
					logger.warning("WWSClient.getData() timeout.");
				} catch (final IOException e) {
					logger.warning("WWSClient.getData() IOException: " + e.getMessage());
				} catch (final NumberFormatException e) {
					logger.warning(
							"WWSClent.getData() couldn't parse server response. Is remote server a Winston Wave Server?");
				}
				return false;
			}
		};
		try {
			ret = rt.go();
		} catch (final UtilException e) {
			// Do nothing
		}
		return ret;
	}

	/**
	 * Retrieve a list of channels from a remote Winston.
	 * 
	 * @return List of channels
	 */
	public List<Channel> getChannels() {
		return getChannels(false);
	}

	/**
	 * Retrieve a list of channels from Winston.
	 * 
	 * @param meta
	 *            if true, request metadata
	 * @return List of channels
	 */
	public List<Channel> getChannels(final boolean meta) {
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		List<Channel> channels = new ArrayList<Channel>();

		try {
			Bootstrap b = new Bootstrap();
			b.group(workerGroup);
			b.channel(NioSocketChannel.class);
			b.option(ChannelOption.SO_KEEPALIVE, true);
			b.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new StringEncoder()).addLast(new WWSClientHandler());
				}
			});

			AttributeKey<WWSCommandHandler> handlerKey = WWSClientHandler.handlerKey;
			// Start the client.
			io.netty.channel.Channel ch = b.connect(host, port).sync().channel();
			String req = String.format("GETCHANNELS: GC%s\r\n", meta ? " METADATA" : "");
			ch.attr(handlerKey).set(new MenuHandler(ch, channels));
			System.err.println("Sending: " + req);
			ChannelFuture lastWriteFuture = ch.writeAndFlush(req);
			// Wait until the connection is closed.
			ch.closeFuture().sync();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(ex);
		} finally {
			workerGroup.shutdownGracefully();
		}

		return channels;
	}

	public Wave getWave(final String station, final String comp, final String network, final String location,
			final double start, final double end, final boolean compress) {
		final String req = String.format(Locale.US, "GETWAVERAW: GS %s %s %s %s %f %f %s\n", station, comp, network,
				(location == null ? "--" : location), start, end, (compress ? "1" : "0"));
		final byte[] buf = getData(req, compress);
		if (buf == null)
			return null;

		return new Wave(ByteBuffer.wrap(buf));
	}

	public HelicorderData getHelicorder(final String station, final String comp, final String network,
			final String location, final double start, final double end, final boolean compress) {
		final String req = String.format(Locale.US, "GETSCNLHELIRAW: GS %s %s %s %s %f %f %s\n", station, comp, network,
				location, start, end, (compress ? "1" : "0"));
		final byte[] buf = getData(req, compress);
		if (buf == null)
			return null;

		return new HelicorderData(ByteBuffer.wrap(buf));
	}

	public String[] getStatus() throws UtilException {
		return getStatus(0d);
	}

	public String[] getStatus(final Double d) throws UtilException {
		final double ageThreshold = d;
		final Retriable<String[]> rt = new Retriable<String[]>("WWSClient.getStatus()", maxRetries) {
			@Override
			public void attemptFix() {
				close();
			}

			@Override
			public boolean attempt() {
				try {
					if (!connected())
						connect();

					final String cmd = "STATUS: GC " + ageThreshold;
					writeString(cmd + "\n");

					final String info = readString();
					String[] ss = info.split(": ");
					final int lines = Integer.parseInt(ss[1]);
					if (lines == 0)
						return true;

					ss = new String[lines];
					for (int i = 0; i < ss.length; i++)
						ss[i] = readString();

					result = ss;
					return true;
				} catch (final SocketTimeoutException e) {
					logger.warning("WWSClient.getStatus() timeout.");
				} catch (final IOException e) {
					logger.warning("WWSClient.getChannels() IOException: " + e.getMessage());
				}
				return false;
			}
		};

		return rt.go();
	}

	public RSAMData getRSAMData(final String station, final String comp, final String network, final String location,
			final double start, final double end, final int period, final boolean compress) {
		final String req = String.format(Locale.US, "GETSCNLRSAMRAW: GS %s %s %s %s %f %f %d %s\n", station, comp,
				network, location, start, end, period, (compress ? "1" : "0"));
		final byte[] buf = getData(req, compress);
		if (buf == null)
			return null;

		return new RSAMData(ByteBuffer.wrap(buf), period);
	}

	public static void outputSac(final String server, final int port, final TimeSpan timespan, final Scnl scnl) {
		final DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		final String date = df.format(new Date(timespan.startTime)) + "-" + df.format(new Date(timespan.endTime));

		String filename = scnl.toString("_") + "_" + date + ".sac";
		outputSac(server, port, timespan, scnl, filename);
	}

	public static void outputSac(final String server, final int port, final TimeSpan timeSpan, final Scnl scnl,
			final String fn) {
		final WWSClient winston = new WWSClient(server, port);
		winston.connect();

		double st = J2kSec.fromEpoch(timeSpan.startTime);
		double et = J2kSec.fromEpoch(timeSpan.endTime);
		Wave wave = winston.getWave(scnl.station, scnl.channel, scnl.network, scnl.location, st, et, false);

		if (wave != null) {
			wave = wave.subset(st, et);

			final SeismicDataFile file = SeismicDataFile.getFile(fn, FileType.SAC);
			String channel = scnl.toString("_");

			file.putWave(channel, wave);
			try {
				file.write();
			} catch (final IOException e) {
				System.err.println("Couldn't write file: " + e.getLocalizedMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("Wave not found");
		}
	}

	public static void outputSac(final String server, final int port, final Double st, final Double et, final String c,
			final String fn, final double gulpSize, final double gulpDelay) {
		final WWSClient winston = new WWSClient(server, port);
		winston.connect();

		final String[] chan = c.split("\\$");
		final String sta = chan[0];
		final String comp = chan[1];
		final String net = chan[2];
		final String loc = chan.length == 4 ? chan[3] : null;

		final List<Wave> waves = new ArrayList<Wave>();

		final double duration = et - st;
		final int N = (int) Math.ceil(duration / gulpSize) - 1;
		double t1 = st;
		double t2 = 0;
		Wave wavelet;
		System.out.printf("Gulp size: %f (s), Gulp delay: %d (ms), Number of gulps: %d\n", gulpSize,
				(long) (gulpDelay * 1000), N + 1);
		for (int i = 0; i < N; i++) {
			t2 = t1 + gulpSize;
			System.out.printf("Gulp #%d starting ... ", i + 1);
			wavelet = winston.getWave(sta, comp, net, loc, t1, t2, false);
			System.out.printf("done.\n");
			if (wavelet != null)
				waves.add(wavelet);
			t1 = t2;
			if (gulpDelay != 0)
				try {
					System.out.printf("Waiting ... ");
					Thread.sleep((long) (gulpDelay * 1000));
					System.out.println("done.");

				} catch (final InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

		}
		t2 = et;
		System.out.printf("Gulp #%d starting ... ", N + 1);
		wavelet = winston.getWave(sta, comp, net, loc, t1, t2, false);
		System.out.printf("done.\n");

		if (wavelet != null)
			waves.add(wavelet);

		Wave wave = Wave.join(waves);

		if (wave != null) {
			wave = wave.subset(st, et);

			final SeismicDataFile file = SeismicDataFile.getFile(fn, FileType.SAC);
			String channel = sta + "_" + comp + "_" + net;
			if (loc != null)
				channel += "_" + loc;

			file.putWave(channel, wave);
			try {
				file.write();
			} catch (final IOException e) {
				System.err.println("Couldn't write file: " + e.getLocalizedMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("Wave not found");
		}
	}

	public static void outputText(final String s, final int p, final TimeSpan timeSpan, final Scnl scnl) {
		System.out.println("dumping samples as text\n");
		final WWSClient winston = new WWSClient(s, p);
		winston.connect();

		double st = J2kSec.fromEpoch(timeSpan.startTime);
		double et = J2kSec.fromEpoch(timeSpan.endTime);
		Wave wave = winston.getWave(scnl.station, scnl.channel, scnl.network, scnl.location, st, et, false);

		if (wave != null) {
			wave = wave.subset(st, et);
			for (final int i : wave.buffer)
				System.out.println(i);

		} else {
			System.out.println("Wave not found");
		}
	}

	public static void displayMenu() {
		WWSClient wws = new WWSClient("pubavo1.wr.usgs.gov", 16022);
		List<Channel> channels = wws.getChannels();
		for (Channel chan : channels) {
			System.out.println(chan.toMetadataString());
		}
		
	}
	public static void main(final String[] args) {
		try {
			final WWSClientArgs config = new WWSClientArgs(args);

			if (config.menu) {
				LOGGER.debug("Requesting menu from {}:{}.", config.server, config.port);
				displayMenu();
			}
			
			if (config.sacOutput) {
				LOGGER.debug("Requesting {} from {}:{} for {} and writing to SAC.", config.channel, config.server,
						config.port, config.timeSpan);
				outputSac(config.server, config.port, config.timeSpan, config.channel);
			}

			if (config.txtOutput) {
				LOGGER.debug("Requesting {} from {}:{} for {} and writing to TXT.", config.channel, config.server,
						config.port, config.timeSpan);
				outputText(config.server, config.port, config.timeSpan, config.channel);
			}
		} catch (Exception e) {
			LOGGER.error(e.getLocalizedMessage());
		}

	}

}
