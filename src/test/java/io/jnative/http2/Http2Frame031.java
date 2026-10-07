package io.jnative.http2;

import java.util.HashMap;
import java.util.Map;

/** Self-contained HTTP/2 frame + session helpers for 0.31 host-side tests. */
final class Http2Frame031 {
    private Http2Frame031() {
    }

    static final class FrameType {
        static final int DATA = 0;
        static final int HEADERS = 1;
        static final int PRIORITY = 2;
        static final int RST_STREAM = 3;
        static final int SETTINGS = 4;
        static final int PUSH_PROMISE = 5;
        static final int PING = 6;
        static final int GOAWAY = 7;
        static final int WINDOW_UPDATE = 8;
        static final int CONTINUATION = 9;
    }

    static final class Frame {
        int length;
        int type;
        int flags;
        int streamId;
        byte[] payload;

        Frame(int type, int flags, int streamId, byte[] payload) {
            this.type = type;
            this.flags = flags;
            this.streamId = streamId;
            this.payload = payload == null ? new byte[0] : payload;
            this.length = this.payload.length;
        }
    }

    static byte[] encode(Frame frame) {
        int len = frame.payload.length;
        byte[] out = new byte[9 + len];
        out[0] = (byte) ((len >> 16) & 255);
        out[1] = (byte) ((len >> 8) & 255);
        out[2] = (byte) (len & 255);
        out[3] = (byte) (frame.type & 255);
        out[4] = (byte) (frame.flags & 255);
        out[5] = (byte) ((frame.streamId >> 24) & 127);
        out[6] = (byte) ((frame.streamId >> 16) & 255);
        out[7] = (byte) ((frame.streamId >> 8) & 255);
        out[8] = (byte) (frame.streamId & 255);
        System.arraycopy(frame.payload, 0, out, 9, len);
        return out;
    }

    static Frame decode(byte[] data, int offset) {
        if (data.length - offset < 9) {
            return null;
        }
        Frame f = new Frame(0, 0, 0, new byte[0]);
        f.length = ((data[offset] & 255) << 16) | ((data[offset + 1] & 255) << 8) | (data[offset + 2] & 255);
        f.type = data[offset + 3] & 255;
        f.flags = data[offset + 4] & 255;
        f.streamId = ((data[offset + 5] & 127) << 24) | ((data[offset + 6] & 255) << 16)
                | ((data[offset + 7] & 255) << 8) | (data[offset + 8] & 255);
        if (data.length - offset < 9 + f.length) {
            return null;
        }
        f.payload = new byte[f.length];
        System.arraycopy(data, offset + 9, f.payload, 0, f.length);
        return f;
    }

    static Frame roundtrip(Frame frame) {
        byte[] encoded = encode(frame);
        return decode(encoded, 0);
    }

    static final class FlowControl {
        int connectionWindow = 65535;
        private final Map<Integer, Integer> streamWindows = new HashMap<>();

        int streamWindow(int streamId) {
            return streamWindows.getOrDefault(streamId, 65535);
        }

        void initStream(int streamId, int initialWindow) {
            streamWindows.put(streamId, initialWindow);
        }

        void consume(int streamId, int bytes) {
            if (bytes < 0) {
                throw new IllegalArgumentException("negative consume");
            }
            if (streamId == 0) {
                if (connectionWindow < bytes) {
                    throw new IllegalStateException("connection flow control exceeded");
                }
                connectionWindow -= bytes;
                return;
            }
            int w = streamWindow(streamId);
            if (w < bytes) {
                throw new IllegalStateException("stream flow control exceeded");
            }
            streamWindows.put(streamId, w - bytes);
        }

        void windowUpdate(int streamId, int increment) {
            if (increment <= 0) {
                throw new IllegalArgumentException("increment must be positive");
            }
            if (streamId == 0) {
                connectionWindow += increment;
                return;
            }
            streamWindows.put(streamId, streamWindow(streamId) + increment);
        }
    }

    enum StreamState {
        IDLE, OPEN, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED
    }

    static final class StreamStateMachine {
        private final Map<Integer, StreamState> streams = new HashMap<>();

        StreamState state(int streamId) {
            return streams.getOrDefault(streamId, StreamState.IDLE);
        }

        void onHeaders(int streamId, boolean endStream) {
            StreamState s = state(streamId);
            if (s == StreamState.IDLE) {
                streams.put(streamId, endStream ? StreamState.HALF_CLOSED_LOCAL : StreamState.OPEN);
                return;
            }
            if (s == StreamState.OPEN) {
                if (endStream) {
                    streams.put(streamId, StreamState.HALF_CLOSED_LOCAL);
                }
                return;
            }
            if (s == StreamState.HALF_CLOSED_REMOTE && endStream) {
                streams.put(streamId, StreamState.CLOSED);
            }
        }

        void onHeadersReceived(int streamId, boolean endStream) {
            StreamState s = state(streamId);
            if (s == StreamState.IDLE) {
                streams.put(streamId, endStream ? StreamState.HALF_CLOSED_REMOTE : StreamState.OPEN);
                return;
            }
            if (s == StreamState.OPEN && endStream) {
                streams.put(streamId, StreamState.HALF_CLOSED_REMOTE);
                return;
            }
            if (s == StreamState.HALF_CLOSED_LOCAL && endStream) {
                streams.put(streamId, StreamState.CLOSED);
            }
        }

        void rstStream(int streamId) {
            streams.put(streamId, StreamState.CLOSED);
        }

        boolean isClosed(int streamId) {
            return state(streamId) == StreamState.CLOSED;
        }
    }

    static final class Session {
        int nextStreamId = 1;
        boolean goawaySent;
        int lastStreamId;
        private final StreamStateMachine streams = new StreamStateMachine();
        private final FlowControl flow = new FlowControl();

        StreamStateMachine streams() {
            return streams;
        }

        FlowControl flow() {
            return flow;
        }

        int allocateStream() {
            if (goawaySent) {
                throw new IllegalStateException("HTTP/2 GOAWAY: refusing new streams");
            }
            int id = nextStreamId;
            nextStreamId += 2;
            lastStreamId = id;
            flow.initStream(id, 65535);
            streams.onHeaders(id, false);
            return id;
        }

        void sendGoAway() {
            goawaySent = true;
        }

        void rstStream(int streamId) {
            streams.rstStream(streamId);
        }
    }
}
