/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CRC of a stream and of a channel read to their end: {@link CrcModel#crc(InputStream)}, {@link CRC#update(InputStream)},
 * {@link CrcModel#crc(ReadableByteChannel)} and {@link CRC#update(ReadableByteChannel)}. The unit tests run with the class
 * directory, where java.util.zip.CRC32 calculates the models of the JDK; StreamIT tests java.util.zip.CRC32C with the jar
 */
class StreamTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the buffer of a call
    private static final int BUFFER = 8192;

    // no bytes, a few bytes, around exactly one buffer, several buffers and a rest
    private static final int[] SIZES = {0, 9, BUFFER - 1, BUFFER, BUFFER + 1, 3 * BUFFER + 1000};

    // the parts that a stream or a channel in parts reads in turn, fewer bytes than asked: shorter and longer than
    // the minimal length of java.util.zip.CRC32 (128 bytes with the class directory), and 0 bytes, which a channel can
    // read, and so can a stream that breaks the contract of InputStream.read
    private static final int[] PARTS = {1, 0, 7, 100, 5000, 3, 0, BUFFER - 1, 200};

    private static final byte[] DATA = new byte[3 * BUFFER + 1000];

    static {
        new Random(128).nextBytes(DATA);
    }

    // the models of the catalogue calculated by the lookup tables, and the models that java.util.zip.CRC32 calculates
    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            CrcModel model = standard.model();
            models.add(model.withJdk(false));
            if (model.withJdk(true).isJdk()) {
                models.add(model.withJdk(true));
            }
        }
        return models;
    }

    // the name of the model for the messages, with the way of the calculation
    private static String name(CrcModel model) {
        return model.isJdk() ? model + " of the JDK" : model.toString();
    }

    // a stream of the data that reads the parts of PARTS in turn; at the end of the data it returns -1, or throws
    // the failure if there is one. It counts the calls of close and the reads that are not given a whole buffer of
    // 8192 bytes
    private static final class PartsStream extends FilterInputStream {

        private final IOException failure;
        private int next;
        private int closed;
        private int otherReads;

        PartsStream(byte[] data, IOException failure) {
            super(new ByteArrayInputStream(data));
            this.failure = failure;
        }

        PartsStream(byte[] data) {
            this(data, null);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b.length != BUFFER || off != 0 || len != BUFFER) {
                otherReads++;
            }
            int read = super.read(b, off, Math.min(len, PARTS[next++ % PARTS.length]));
            if (read < 0 && failure != null) {
                throw failure;
            }
            return read;
        }

        @Override
        public void close() {
            closed++;
        }

    }

    // a channel of the data that reads the parts of PARTS in turn, as PartsStream
    private static final class PartsChannel implements ReadableByteChannel {

        private final ByteBuffer data;
        private final IOException failure;
        private int next;
        private int closed;
        private int otherReads;

        PartsChannel(byte[] data, IOException failure) {
            this.data = ByteBuffer.wrap(data);
            this.failure = failure;
        }

        PartsChannel(byte[] data) {
            this(data, null);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (dst.capacity() != BUFFER || dst.position() != 0 || dst.remaining() != BUFFER) {
                otherReads++;
            }
            if (!data.hasRemaining()) {
                if (failure != null) {
                    throw failure;
                }
                return -1;
            }
            int part = Math.min(Math.min(dst.remaining(), data.remaining()), PARTS[next++ % PARTS.length]);
            ByteBuffer slice = data.duplicate();
            slice.limit(slice.position() + part);
            dst.put(slice);
            data.position(data.position() + part);
            return part;
        }

        @Override
        public boolean isOpen() {
            return closed == 0;
        }

        @Override
        public void close() {
            closed++;
        }

    }

    @Test
    void testStreams() throws IOException {
        for (CrcModel model : models()) {
            CRC first = model.crc(CHECK);
            for (int size : SIZES) {
                byte[] data = Arrays.copyOf(DATA, size);
                String message = name(model) + ", a stream of " + size + " bytes";
                CRC expected = model.crc(data);
                assertEquals(expected, model.crc(new ByteArrayInputStream(data)), message + ": wrong CRC");
                assertEquals(first.update(data), first.update(new ByteArrayInputStream(data)), message + ": wrong CRC after other data");
                try (PartsStream in = new PartsStream(data)) {
                    assertEquals(expected, model.crc(in), message + " in parts: wrong CRC");
                    assertEquals(0, in.closed, message + ": the stream has not got to be closed");
                    assertEquals(0, in.otherReads, message + ": every read has to be given a whole buffer of 8192 bytes");
                }
                try (PartsStream in = new PartsStream(data)) {
                    assertEquals(first.update(data), first.update(in), message + " in parts: wrong CRC after other data");
                    assertEquals(0, in.closed, message + ": update has not got to close the stream");
                }
            }
        }
    }

    @Test
    void testChannels() throws IOException {
        for (CrcModel model : models()) {
            CRC first = model.crc(CHECK);
            for (int size : SIZES) {
                byte[] data = Arrays.copyOf(DATA, size);
                String message = name(model) + ", a channel of " + size + " bytes";
                CRC expected = model.crc(data);
                try (ReadableByteChannel channel = Channels.newChannel(new ByteArrayInputStream(data))) {
                    assertEquals(expected, model.crc(channel), message + ": wrong CRC");
                }
                try (PartsChannel channel = new PartsChannel(data)) {
                    assertEquals(expected, model.crc(channel), message + " in parts: wrong CRC");
                    assertTrue(channel.isOpen(), message + ": the channel has not got to be closed");
                    assertEquals(0, channel.otherReads, message + ": every read has to be given a whole buffer of 8192 bytes");
                }
                try (PartsChannel channel = new PartsChannel(data)) {
                    assertEquals(first.update(data), first.update(channel), message + " in parts: wrong CRC after other data");
                    assertTrue(channel.isOpen(), message + ": update has not got to close the channel");
                }
            }
        }
    }

    @Test
    void testEmptyStreamAndChannel() throws IOException {
        CrcModel model = CrcModel.of("CRC-32C");
        CRC crc = model.crc(CHECK);
        byte[] none = new byte[0];
        assertSame(model.crc(), model.crc(new ByteArrayInputStream(none)), "An empty stream has to give the CRC of the empty data");
        assertSame(crc, crc.update(new ByteArrayInputStream(none)), "An empty stream has to give the same CRC");
        try (PartsChannel channel = new PartsChannel(none)) {
            assertSame(model.crc(), model.crc(channel), "An empty channel has to give the CRC of the empty data");
        }
        try (PartsChannel channel = new PartsChannel(none)) {
            assertSame(crc, crc.update(channel), "An empty channel has to give the same CRC");
        }
        // the parts of 1, 0 and 7 bytes of PARTS: a read of 0 bytes before the end
        byte[] eight = Arrays.copyOf(DATA, 8);
        try (PartsStream in = new PartsStream(eight)) {
            assertEquals(model.crc(eight), model.crc(in), "A read of 0 bytes has not got to end the stream");
        }
        try (PartsChannel channel = new PartsChannel(eight)) {
            assertEquals(model.crc(eight), model.crc(channel), "A read of 0 bytes has not got to end the channel");
        }
    }

    @Test
    void testFile(@TempDir final Path directory) throws IOException {
        Path file = Files.write(directory.resolve("data"), DATA);
        List<CrcModel> models = Arrays.asList(CrcModel.of("CRC-32", true), CrcModel.of("CRC-32", false), CrcModel.of("CRC-16/ARC"),
                CrcModel.of("CRC-64/XZ"));
        for (CrcModel model : models) {
            String message = name(model);
            try (InputStream in = Files.newInputStream(file)) {
                assertEquals(model.crc(DATA), model.crc(in), message + ": wrong CRC of the stream of the file");
                assertEquals(-1, in.read(), message + ": the stream has to be read to its end and stay open");
            }
            try (FileChannel channel = FileChannel.open(file)) {
                assertEquals(model.crc(DATA), model.crc(channel), message + ": wrong CRC of the channel of the file");
                assertTrue(channel.isOpen(), message + ": the channel of the file has not got to be closed");
                assertEquals(DATA.length, channel.position(), message + ": the channel has to be read to its end");
            }
            CRC header = model.crc(CHECK);
            try (FileChannel channel = FileChannel.open(file)) {
                assertEquals(header.update(DATA), header.update(channel), message + ": wrong CRC of the header followed by the channel");
            }
        }
    }

    @Test
    void testSelectableChannel() throws IOException, InterruptedException, ExecutionException {
        CrcModel model = CrcModel.of("CRC-32");
        Pipe pipe = Pipe.open();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Pipe.SourceChannel source = pipe.source()) {
            // written by another thread: the pipe holds fewer bytes than the data, and a read gives the bytes available
            Future<Integer> written = executor.submit(() -> {
                try (Pipe.SinkChannel sink = pipe.sink()) {
                    ByteBuffer data = ByteBuffer.wrap(DATA);
                    while (data.hasRemaining()) {
                        sink.write(data);
                    }
                }
                return DATA.length;
            });
            assertTrue(source.isBlocking(), "The channel of a pipe has to be in blocking mode");
            assertEquals(model.crc(DATA), model.crc(source), "Wrong CRC of the channel of a pipe");
            assertEquals(DATA.length, written.get(), "The data has to be written into the pipe");
            assertTrue(source.isOpen(), "The channel of the pipe has not got to be closed");
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "The thread has to end");
        }
    }

    @Test
    void testNonBlockingChannel() throws IOException {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        CRC crc = model.crc(DATA, 0, 100);
        Pipe pipe = Pipe.open();
        try (Pipe.SinkChannel sink = pipe.sink()) {
            assertEquals(CHECK.length, sink.write(ByteBuffer.wrap(CHECK)), "The bytes have to be written into the pipe");
        }
        try (Pipe.SourceChannel source = pipe.source()) {
            source.configureBlocking(false);
            assertThrows(IllegalBlockingModeException.class, () -> model.crc(source), "A channel in non-blocking mode has to be rejected");
            assertThrows(IllegalBlockingModeException.class, () -> crc.update(source), "A channel in non-blocking mode has to be rejected");
            assertTrue(source.isOpen(), "The rejected channel has not got to be closed");
            source.configureBlocking(true);
            assertEquals(model.crc(CHECK), model.crc(source), "The rejected channel has not got to be read");
        }
    }

    @Test
    void testIOException() throws IOException {
        CrcModel model = CrcModel.of("CRC-64/XZ");
        CRC crc = model.crc(CHECK);
        IOException failure = new IOException("the stream fails after the data");
        try (PartsStream in = new PartsStream(DATA, failure)) {
            assertSame(failure, assertThrows(IOException.class, () -> model.crc(in), "crc(InputStream) has to throw the exception"),
                    "The IOException of the stream has to be passed on");
            assertEquals(0, in.closed, "The failed stream has not got to be closed");
        }
        try (PartsStream in = new PartsStream(DATA, failure)) {
            assertSame(failure, assertThrows(IOException.class, () -> crc.update(in), "update(InputStream) has to throw the exception"),
                    "The IOException of the stream has to be passed on");
        }
        try (PartsChannel channel = new PartsChannel(DATA, failure)) {
            assertSame(failure, assertThrows(IOException.class, () -> model.crc(channel), "crc(ReadableByteChannel) has to throw the exception"),
                    "The IOException of the channel has to be passed on");
            assertTrue(channel.isOpen(), "The failed channel has not got to be closed");
        }
        try (PartsChannel channel = new PartsChannel(DATA, failure)) {
            assertSame(failure, assertThrows(IOException.class, () -> crc.update(channel), "update(ReadableByteChannel) has to throw the exception"),
                    "The IOException of the channel has to be passed on");
        }
    }

    @Test
    void testNull() {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        CRC crc = model.crc();
        InputStream noStream = null;
        ReadableByteChannel noChannel = null;
        NullPointerException modelStream = assertThrows(NullPointerException.class, () -> model.crc(noStream),
                "CrcModel.crc(null stream) has to throw NullPointerException");
        assertEquals("CrcModel::crc - in is null", modelStream.getMessage(), "Wrong message");
        NullPointerException modelChannel = assertThrows(NullPointerException.class, () -> model.crc(noChannel),
                "CrcModel.crc(null channel) has to throw NullPointerException");
        assertEquals("CrcModel::crc - channel is null", modelChannel.getMessage(), "Wrong message");
        NullPointerException crcStream = assertThrows(NullPointerException.class, () -> crc.update(noStream),
                "CRC.update(null stream) has to throw NullPointerException");
        assertEquals("CRC::update - in is null", crcStream.getMessage(), "Wrong message");
        NullPointerException crcChannel = assertThrows(NullPointerException.class, () -> crc.update(noChannel),
                "CRC.update(null channel) has to throw NullPointerException");
        assertEquals("CRC::update - channel is null", crcChannel.getMessage(), "Wrong message");
    }

    @Test
    void testLengthOverflow() throws IOException {
        CRC crc = CrcModel.of("CRC-32").crc(0x0L, Long.MAX_VALUE);
        try (PartsStream in = new PartsStream(CHECK)) {
            assertThrows(ArithmeticException.class, () -> crc.update(in), "The length of update(InputStream) has to exceed Long.MAX_VALUE");
        }
        try (PartsChannel channel = new PartsChannel(CHECK)) {
            assertThrows(ArithmeticException.class, () -> crc.update(channel), "The length of update(ReadableByteChannel) has to exceed Long.MAX_VALUE");
        }
        assertSame(crc, crc.update(new ByteArrayInputStream(new byte[0])), "An empty stream has to give the same CRC of the longest data");
        try (PartsChannel channel = new PartsChannel(new byte[0])) {
            assertSame(crc, crc.update(channel), "An empty channel has to give the same CRC of the longest data");
        }
    }

}
