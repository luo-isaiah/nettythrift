/**
 * 
 */
package io.nettythrift;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.nettythrift.transport.TNiftyTransport;
import io.nettythrift.transport.ThriftTransportType;

/**
 * @author HouKangxi
 *
 */
public class ThriftServerHandler extends SimpleChannelInboundHandler<ThriftMessage> {
	private final ServerConfig serverDef;
	// private final AtomicInteger dispatcherSequenceId = new AtomicInteger(0);
	// private final AtomicInteger lastResponseWrittenId = new AtomicInteger(0);

	public ThriftServerHandler(ServerConfig serverDef) {
		this.serverDef = serverDef;
	}

	@Override
	protected void messageReceived(final ChannelHandlerContext ctx, final ThriftMessage message) throws Exception {
		// System.out.printf("ThriftServerHandler: channelRead0( message = %s),
		// readable? %s, frameSize = %d\n" , message
		// ,message.getBuffer().isReadable(),message.getBuffer().readableBytes());
		if (message.readResult != null) {
			final TNiftyTransport messageTransport = new TNiftyTransport(ctx.channel(), null, TNiftyTransport.MOD_W);
			TProtocolFactory proctocolFactory = message.getProctocolFactory();
			TProtocol outProtocol = proctocolFactory.getProtocol(messageTransport);
			serverDef.getProcessor().write(new NioWriterFlusher() {
				@Override
				public io.netty.util.concurrent.EventExecutor handlerContextExecutor() {
					return ctx.executor();
				}

				@Override
				public void doFlush(int code, String respMessage) {
					ThriftMessage response = message.clone(messageTransport.getOutputBuffer());
					response.responseCode = code;
					response.responseMessage = respMessage;
					ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
				}

				@Override
				public ThriftTransportType transportType() {
					return message.getTransportType();
				}
			}, outProtocol, serverDef, message.getProxyInfo(), message.readResult);
			return;
		}
		TNiftyTransport msgTrans = new TNiftyTransport(ctx.channel(), message.getBuffer(), TNiftyTransport.MOD_RW);
		TProtocolFactory proctocolFactory = message.getProctocolFactory();
		TProtocol inProtocol = proctocolFactory.getProtocol(msgTrans);
		TProtocol outProtocol = proctocolFactory.getProtocol(msgTrans);

		processRequest(ctx, message, msgTrans, inProtocol, outProtocol);
	}

	private void processRequest(final ChannelHandlerContext ctx, final ThriftMessage message,
			final TNiftyTransport messageTransport, final TProtocol inProtocol, final TProtocol outProtocol) {
		// final int requestSequenceId = dispatcherSequenceId.incrementAndGet();
		try {
			serverDef.getProcessor().process(new NioWriterFlusher() {
				@Override
				public io.netty.util.concurrent.EventExecutor handlerContextExecutor() {
					return ctx.executor();
				}

				@Override
				public void doFlush(int code, String respMessage) {
					ThriftMessage response = message.clone(messageTransport.getOutputBuffer());
					response.responseCode = code;
					response.responseMessage = respMessage;
					ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
				}

				@Override
				public ThriftTransportType transportType() {
					return message.getTransportType();
				}
			}, inProtocol, outProtocol, serverDef, message.getProxyInfo());
		} catch (TException e) {
			e.printStackTrace();
		}
	}
}