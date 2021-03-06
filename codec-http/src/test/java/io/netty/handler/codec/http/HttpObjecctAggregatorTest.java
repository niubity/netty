/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedMessageChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HttpObjecctAggregatorTest {

    @Test
    public void testAggregate() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedMessageChannel embedder = new EmbeddedMessageChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpHeaders.setHeader(message, "X-Test", true);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpContent chunk3 = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a messageReceived event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        DefaultFullHttpRequest aggratedMessage = (DefaultFullHttpRequest) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.data().readableBytes() + chunk2.data().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
        assertNull(embedder.readInbound());

    }

    private static void checkContentBuffer(DefaultFullHttpRequest aggregatedMessage) {
        CompositeByteBuf buffer = (CompositeByteBuf) aggregatedMessage.data();
        assertEquals(2, buffer.numComponents());
        List<ByteBuf> buffers = buffer.decompose(0, buffer.capacity());
        assertEquals(2, buffers.size());
        for (ByteBuf buf: buffers) {
            // This should be false as we decompose the buffer before to not have deep hierarchy
            assertFalse(buf instanceof CompositeByteBuf);
        }
    }

    @Test
    public void testAggregateWithTrailer() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedMessageChannel embedder = new EmbeddedMessageChannel(aggr);
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpHeaders.setHeader(message, "X-Test", true);
        HttpHeaders.setTransferEncodingChunked(message);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        LastHttpContent trailer = new DefaultLastHttpContent();
        trailer.trailingHeaders().set("X-Trailer", true);

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a messageReceived event so return true
        assertTrue(embedder.writeInbound(trailer));
        assertTrue(embedder.finish());
        DefaultFullHttpRequest aggratedMessage = (DefaultFullHttpRequest) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.data().readableBytes() + chunk2.data().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        assertEquals(aggratedMessage.headers().get("X-Trailer"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);

        assertNull(embedder.readInbound());

    }


    @Test(expected = TooLongFrameException.class)
    public void testTooLongFrameException() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(4);
        EmbeddedMessageChannel embedder = new EmbeddedMessageChannel(aggr);
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        embedder.writeInbound(chunk2);
        fail();

    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConstructorUsage() {
        new HttpObjectAggregator(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMaxCumulationBufferComponents() {
        HttpObjectAggregator aggr= new HttpObjectAggregator(Integer.MAX_VALUE);
        aggr.setMaxCumulationBufferComponents(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMaxCumulationBufferComponentsAfterInit() throws Exception {
        HttpObjectAggregator aggr = new HttpObjectAggregator(Integer.MAX_VALUE);
        ChannelHandlerContext ctx = EasyMock.createMock(ChannelHandlerContext.class);
        EasyMock.replay(ctx);
        aggr.beforeAdd(ctx);
        aggr.setMaxCumulationBufferComponents(10);
    }
}
