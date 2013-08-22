/**
 * Copyright 2012 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
package com.jogamp.opengl.util.av;

import java.net.URI;

import javax.media.opengl.GL;
import javax.media.opengl.GLException;

import jogamp.opengl.Debug;

import com.jogamp.opengl.util.texture.TextureSequence;

/**
 * GLMediaPlayer interface specifies a {@link TextureSequence} state machine
 * using a multiplexed audio/video stream as it's source.
 * <p>
 * Audio maybe supported and played back internally or via an {@link AudioSink} implementation.
 * </p>
 * <p>
 * Audio and video streams can be selected or muted via {@link #initStream(URI, int, int, int)}
 * using the appropriate <a href="#streamIDs">stream id</a>'s.
 * </p>
 *   
 * <a name="streamworker"><h5><i>StreamWorker</i> Decoding Thread</h5></a>
 * <p>
 * Most of the stream processing is performed on the decoding thread, a.k.a. <i>StreamWorker</i>:
 * <ul>
 *   <li>Stream initialization triggered by {@link #initStream(URI, int, int, int) initStream(..)} - User gets notified whether the stream has been initialized or not via {@link GLMediaEventListener#attributesChanged(GLMediaPlayer, int, long) attributesChanges(..)}.</li>
 *   <li>Stream decoding - User gets notified of a new frame via {@link GLMediaEventListener#newFrameAvailable(GLMediaPlayer, com.jogamp.opengl.util.texture.TextureSequence.TextureFrame, long) newFrameAvailable(...)}.</li>
 *   <li>Caught <a href="#streamerror">exceptions on the decoding thread</a> are delivered as {@link StreamException}s.</li>
 * </ul>
 * <i>StreamWorker</i> generates it's own {@link GLContext}, shared with the one passed to {@link #initGL(GL)}.
 * The shared {@link GLContext} allows the decoding thread to push the video frame data directly into 
 * the designated {@link TextureFrame}, later returned via {@link #getNextTexture(GL)} and used by the user.
 * </p>
 * <a name="streamerror"><h7><i>StreamWorker</i> Error Handling</h7></a>
 * <p>
 * Caught exceptions on <a href="#streamworker">StreamWorker</a> are delivered as {@link StreamException}s,
 * which either degrades the {@link State} to {@link State#Uninitialized} or {@link State#Paused}.
 * </p>
 * <p>
 * An occurring {@link StreamException} triggers a {@link GLMediaEventListener#EVENT_CHANGE_ERR EVENT_CHANGE_ERR} event,
 * which can be listened to via {@link GLMediaEventListener#attributesChanged(GLMediaPlayer, int, long)}. 
 * </p>
 * <p>
 * An occurred {@link StreamException} can be read via {@link #getStreamException()}. 
 * </p>
 * 
 * </p>
 * <a name="lifecycle"><h5>GLMediaPlayer Lifecycle</h5></a>
 * <p>
 * <table border="1">
 *   <tr><th>Action</th>                                               <th>{@link State} Before</th>                                        <th>{@link State} After</th>                                                                                                       <th>{@link GLMediaEventListener Event}</th></tr>
 *   <tr><td>{@link #initStream(URI, int, int, int)}</td>              <td>{@link State#Uninitialized Uninitialized}</td>                   <td>{@link State#Initialized Initialized}<sup><a href="#streamworker">1</a></sup>, {@link State#Uninitialized Uninitialized}</td> <td>{@link GLMediaEventListener#EVENT_CHANGE_INIT EVENT_CHANGE_INIT} or ( {@link GLMediaEventListener#EVENT_CHANGE_ERR EVENT_CHANGE_ERR} + {@link GLMediaEventListener#EVENT_CHANGE_UNINIT EVENT_CHANGE_UNINIT} )</td></tr>
 *   <tr><td>{@link #initGL(GL)}</td>                                  <td>{@link State#Initialized Initialized}</td>                       <td>{@link State#Paused Paused}, {@link State#Initialized Initialized}</td>                                                        <td>{@link GLMediaEventListener#EVENT_CHANGE_PAUSE EVENT_CHANGE_PAUSE}</td></tr>
 *   <tr><td>{@link #play()}</td>                                      <td>{@link State#Paused Paused}</td>                                 <td>{@link State#Playing Playing}</td>                                                                                             <td>{@link GLMediaEventListener#EVENT_CHANGE_PLAY EVENT_CHANGE_PLAY}</td></tr>
 *   <tr><td>{@link #pause()}</td>                                     <td>{@link State#Playing Playing}</td>                               <td>{@link State#Paused Paused}</td>                                                                                               <td>{@link GLMediaEventListener#EVENT_CHANGE_PAUSE EVENT_CHANGE_PAUSE}</td></tr>
 *   <tr><td>{@link #seek(int)}</td>                                   <td>{@link State#Paused Paused}, {@link State#Playing Playing}</td>  <td>{@link State#Paused Paused}, {@link State#Playing Playing}</td>                                                                <td>none</td></tr>
 *   <tr><td>{@link #getNextTexture(GL)}</td>                          <td>{@link State#Paused Paused}, {@link State#Playing Playing}</td>  <td>{@link State#Paused Paused}, {@link State#Playing Playing}</td>                                                                <td>none</td></tr>
 *   <tr><td>{@link #getLastTexture()}</td>                            <td>{@link State#Paused Paused}, {@link State#Playing Playing}</td>  <td>{@link State#Paused Paused}, {@link State#Playing Playing}</td>                                                                <td>none</td></tr>
 *   <tr><td>{@link TextureFrame#END_OF_STREAM_PTS END_OF_STREAM}</td> <td>{@link State#Playing Playing}</td>                               <td>{@link State#Paused Paused}</td>                                                                                               <td>{@link GLMediaEventListener#EVENT_CHANGE_EOS EVENT_CHANGE_EOS} + {@link GLMediaEventListener#EVENT_CHANGE_PAUSE EVENT_CHANGE_PAUSE}</td></tr>
 *   <tr><td>{@link StreamException}</td>                              <td>ANY</td>                                                         <td>{@link State#Paused Paused}, {@link State#Uninitialized Uninitialized}</td>                                                    <td>{@link GLMediaEventListener#EVENT_CHANGE_ERR EVENT_CHANGE_ERR} + ( {@link GLMediaEventListener#EVENT_CHANGE_PAUSE EVENT_CHANGE_PAUSE} or {@link GLMediaEventListener#EVENT_CHANGE_UNINIT EVENT_CHANGE_UNINIT} )</td></tr>
 *   <tr><td>{@link #destroy(GL)}</td>                                 <td>ANY</td>                                                         <td>{@link State#Uninitialized Uninitialized}</td>                                                                                 <td>{@link GLMediaEventListener#EVENT_CHANGE_UNINIT EVENT_CHANGE_UNINIT}</td></tr>
 * </table>
 * </p>
 * 
 * <a name="streamIDs"><h5>Audio and video Stream IDs</h5></a>
 * <p>
 * <table border="1">
 *   <tr><th>value</th>                    <th>request</th>             <th>get</th></tr>
 *   <tr><td>{@link #STREAM_ID_NONE}</td>  <td>mute</td>                <td>not available</td></tr>
 *   <tr><td>{@link #STREAM_ID_AUTO}</td>  <td>auto</td>                <td>unspecified</td></tr>
 *   <tr><td>&ge;0</td>                    <td>specific stream</td>     <td>specific stream</td></tr>
 * </table>
 * </p>
 * <p>
 * Current implementations (check each API doc link for details):
 * <ul>
 *   <li>{@link jogamp.opengl.util.av.NullGLMediaPlayer}</li>
 *   <li>{@link jogamp.opengl.util.av.impl.OMXGLMediaPlayer}</li>
 *   <li>{@link jogamp.opengl.util.av.impl.FFMPEGMediaPlayer}</li>
 *   <li>{@link jogamp.opengl.android.av.AndroidGLMediaPlayerAPI14}</li> 
 * </ul>
 * </p>
 * <p>
 * Implementations of this interface must implement:
 * <pre>
 *    public static final boolean isAvailable();
 * </pre>
 * to be properly considered by {@link GLMediaPlayerFactory#create(ClassLoader, String)}
 * and {@link GLMediaPlayerFactory#createDefault()}.
 * </p>
 * <p>
 * Variable type, value range and dimension has been chosen to suit embedded CPUs
 * and characteristics of audio and video streaming.
 * Milliseconds of type integer with a maximum value of {@link Integer#MAX_VALUE} 
 * will allow tracking time up 2,147,483.647 seconds or
 * 24 days 20 hours 31 minutes and 23 seconds.
 * Milliseconds granularity is also more than enough to deal with A-V synchronization,
 * where the threshold usually lies within 22ms. 
 * </p>
 * 
 * <a name="synchronization"><h5>Audio and video synchronization</h5></a>
 * <p>
 * The class follows a passive A/V synchronization pattern.
 * Audio is being untouched, while {@link #getNextTexture(GL)} delivers a new video frame
 * only, if its timestamp is less than {@link #MAXIMUM_VIDEO_ASYNC} ahead of <i>time</i>.
 * If its timestamp is more than {@link #MAXIMUM_VIDEO_ASYNC} ahead of <i>time</i>,
 * the previous frame is returned.
 * If its timestamp is more than {@link #MAXIMUM_VIDEO_ASYNC} after <i>time</i>,
 * the frame is dropped and the next frame is being fetched.
 * </p>
 * <p>
 * https://en.wikipedia.org/wiki/Audio_to_video_synchronization
 * <pre>
 *   d_av = v_pts - a_pts;
 * </pre>
 * </p>
 * <p>
 * Recommendation of audio/video pts time lead/lag at production:
 * <ul>
 *   <li>Overall:    +40ms and -60ms  audio ahead video / audio after video</li>
 *   <li>Each stage:  +5ms and -15ms. audio ahead video / audio after video</li>
 * </ul>
 * </p>
 * <p>
 * Recommendation of av pts time lead/lag at presentation:
 * <ul>
 *   <li>TV:         +15ms and -45ms. audio ahead video / audio after video.</li>
 *   <li>Film:       +22ms and -22ms. audio ahead video / audio after video.</li>
 * </ul>
 * </p>
 * 
 * <a name="teststreams"><h5>Test Streams</h5></a>
 * <p>
 * <table border="1">
 *   <tr><th colspan=5>Big Buck Bunny 24f 16:9</th></tr>
 *   <tr><td>Big Buck Bunny</td><td>320p</td><td>h264<td>aac 48000Hz 2 chan</td><td>http://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4</td></tr>
 *   <tr><td>Big Buck Bunny</td><td>720p</td><td>mpeg4<td>ac3 48000Hz 5.1 chan</td><td>http://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_720p_surround.avi</td></tr>
 *   <tr><td>Big Buck Bunny</td><td>720p</td><td>msmpeg4v2<td>mp3 48000Hz 2 chan</td><td>http://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_720p_stereo.avi</td></tr>
 *   <tr><td>Big Buck Bunny</td><td>720p</td><td>theora<td>vorbis 48000Hz 2 chan</td><td>http://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_720p_stereo.ogg</td></tr>
 *   <tr><td>Big Buck Bunny</td><td>1080p</td><td>mpeg4<td>ac3 48000Hz 5.1 chan</td><td>http://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_surround.avi</td></tr>
 *   <tr><th colspan=5>WebM/Matroska (vp8/vorbis)</th></tr>
 *   <tr><td>Big Buck Bunny Trailer</td><td>640p</td><td>vp8<td>vorbis 44100Hz 1 chan</td><td>http://video.webmfiles.org/big-buck-bunny_trailer.webm</td></tr>
 *   <tr><td>Elephants Dream</td><td>540p</td><td>vp8<td>vorbis 44100Hz 1 chan</td><td>http://video.webmfiles.org/elephants-dream.webm</td></tr>
 *   <tr><th colspan=5>You Tube http/rtsp</th></tr>
 *   <tr><td>Sintel</td><td colspan=3>http://www.youtube.com/watch?v=eRsGyueVLvQ</td><td>rtsp://v3.cache1.c.youtube.com/CiILENy73wIaGQn0LpXnygYbeRMYDSANFEgGUgZ2aWRlb3MM/0/0/0/video.3gp</td></tr>
 *   <tr><th colspan=5>Audio/Video Sync</th></tr>
 *   <tr><td>Five-minute-sync-test1080p</td><td colspan=3>https://www.youtube.com/watch?v=szoOsG9137U</td><td>rtsp://v7.cache8.c.youtube.com/CiILENy73wIaGQm133VvsA46sxMYDSANFEgGUgZ2aWRlb3MM/0/0/0/video.3gp</td></tr>
 *   <tr><td>Audio-Video-Sync-Test-Calibration-23.98fps-24fps</td><td colspan=4>https://www.youtube.com/watch?v=cGgf_dbDMsw</td></tr>
 *   <tr><td>sound_in_sync_test</td><td colspan=4>https://www.youtube.com/watch?v=O-zIZkhXNLE</td></tr>
 *   <!-- <tr><td> title </td><td>1080p</td><td>mpeg4<td>ac3 48000Hz 5.1 chan</td><td> url </td></tr> -->
 *   <!-- <tr><td> title </td><td colspan=3> url1 </td><td> url2 </td></tr>
 * </table>
 * </p>
 */
public interface GLMediaPlayer extends TextureSequence {
    public static final boolean DEBUG = Debug.debug("GLMediaPlayer");
    public static final boolean DEBUG_NATIVE = Debug.debug("GLMediaPlayer.Native");
    
    /** Minimum texture count, value {@value}. */
    public static final int TEXTURE_COUNT_MIN = 4;
    
    /** Constant {@value} for <i>mute</i> or <i>not available</i>. See <a href="#streamIDs">Audio and video Stream IDs</a>. */
    public static final int STREAM_ID_NONE = -2;
    /** Constant {@value} for <i>auto</i> or <i>unspecified</i>. See <a href="#streamIDs">Audio and video Stream IDs</a>. */
    public static final int STREAM_ID_AUTO = -1;
    
    /** Maximum video frame async of {@value} milliseconds. */
    public static final int MAXIMUM_VIDEO_ASYNC = 22;
        
    /**
     * A StreamException encapsulates a caught exception in the decoder thread, a.k.a <i>StreamWorker</i>,
     * see See <a href="#streamerror"><i>StreamWorker</i> Error Handling</a>.
     */
    @SuppressWarnings("serial")
    public static class StreamException extends Exception {
        public StreamException(Throwable cause) {
            super(cause);
        }
        public StreamException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    public interface GLMediaEventListener extends TexSeqEventListener<GLMediaPlayer> {
    
        /** State changed to {@link State#Initialized}. See <a href="#lifecycle">Lifecycle</a>.*/
        static final int EVENT_CHANGE_INIT   = 1<<0;
        /** State changed to {@link State#Uninitialized}. See <a href="#lifecycle">Lifecycle</a>.*/
        static final int EVENT_CHANGE_UNINIT = 1<<1;
        /** State changed to {@link State#Playing}. See <a href="#lifecycle">Lifecycle</a>.*/
        static final int EVENT_CHANGE_PLAY   = 1<<2;
        /** State changed to {@link State#Paused}. See <a href="#lifecycle">Lifecycle</a>.*/
        static final int EVENT_CHANGE_PAUSE  = 1<<3;
        /** End of stream reached. See <a href="#lifecycle">Lifecycle</a>.*/
        static final int EVENT_CHANGE_EOS    = 1<<4;
        /** An error occurred, e.g. during off-thread initialization. See {@link StreamException} and <a href="#lifecycle">Lifecycle</a>. */
        static final int EVENT_CHANGE_ERR    = 1<<5;
        
        /** Stream video id change. */
        static final int EVENT_CHANGE_VID    = 1<<16;
        /** Stream audio id change. */
        static final int EVENT_CHANGE_AID    = 1<<17;
        /** TextureFrame size change. */
        static final int EVENT_CHANGE_SIZE   = 1<<18;
        /** Stream fps change. */
        static final int EVENT_CHANGE_FPS    = 1<<19;
        /** Stream bps change. */
        static final int EVENT_CHANGE_BPS    = 1<<20;
        /** Stream length change. */
        static final int EVENT_CHANGE_LENGTH = 1<<21;
        /** Stream codec change. */
        static final int EVENT_CHANGE_CODEC  = 1<<22;
    
        /**
         * @param mp the event source 
         * @param event_mask the changes attributes
         * @param when system time in msec. 
         */
        public void attributesChanged(GLMediaPlayer mp, int event_mask, long when);    
    }
    
    /**
     * See <a href="#lifecycle">Lifecycle</a>.
     */
    public enum State {
        /** Uninitialized player, no resources shall be hold. */
        Uninitialized(0),
        /** Stream has been initialized, user may play or call {@link #initGL(GL)}. */ 
        Initialized(1), 
        /** Stream is playing. */
        Playing(2),
        /** Stream is pausing. */
        Paused(3);
        
        public final int id;

        State(int id){
            this.id = id;
        }
    }
    
    public int getTextureCount();
    
    /** Returns the texture target used by implementation. */
    public int getTextureTarget();

    /** Sets the texture unit. Defaults to 0. */
    public void setTextureUnit(int u);
    
    /** Sets the texture min-mag filter, defaults to {@link GL#GL_NEAREST}. */
    public void setTextureMinMagFilter(int[] minMagFilter);
    /** Sets the texture min-mag filter, defaults to {@link GL#GL_CLAMP_TO_EDGE}. */
    public void setTextureWrapST(int[] wrapST);
    
    /** 
     * Issues asynchronous stream initialization.
     * <p>
     * <a href="#lifecycle">Lifecycle</a>: {@link State#Uninitialized} -> {@link State#Initialized}<sup><a href="#streamworker">1</a></sup> or {@link State#Uninitialized}
     * </p>
     * <p>
     * {@link State#Initialized} is reached asynchronous, 
     * i.e. user gets notified via {@link GLMediaEventListener#attributesChanged(GLMediaPlayer, int, long) attributesChanges(..)}.
     * </p>
     * <p>
     * A possible caught asynchronous {@link StreamException} while initializing the stream off-thread
     * will be thrown at {@link #initGL(GL)}.
     * </p>
     * <p>
     * Muted audio can be achieved by passing {@link #STREAM_ID_NONE} to <code>aid</code>.
     * </p>
     * <p>
     * Muted video can be achieved by passing {@link #STREAM_ID_NONE} to <code>vid</code>,
     * in which case <code>textureCount</code> is ignored as well as the passed GL object of the subsequent {@link #initGL(GL)} call. 
     * </p>
     * @param streamLoc the stream location
     * @param vid video stream id, see <a href="#streamIDs">audio and video Stream IDs</a>
     * @param aid video stream id, see <a href="#streamIDs">audio and video Stream IDs</a>
     * @param textureCount desired number of buffered textures to be decoded off-thread, will be validated by implementation.
     *        The minimum value is {@link #TEXTURE_COUNT_MIN}.
     *        Ignored if video is muted.
     * @throws IllegalStateException if not invoked in {@link State#Uninitialized} 
     * @throws IllegalArgumentException if arguments are invalid
     */
    public void initStream(URI streamLoc, int vid, int aid, int textureCount) throws IllegalStateException, IllegalArgumentException;
    
    /**
     * Returns the {@link StreamException} caught in the decoder thread, or <code>null</code>.
     * @see GLMediaEventListener#EVENT_CHANGE_ERR
     * @see StreamException
     */
    public StreamException getStreamException();
    
    /** 
     * Initializes OpenGL related resources.
     * <p>
     * <a href="#lifecycle">Lifecycle</a>: {@link State#Initialized} -> {@link State#Paused} or {@link State#Initialized}
     * </p>
     * Argument <code>gl</code> is ignored if video is muted, see {@link #initStream(URI, int, int, int)}.
     * 
     * @param gl current GL object. Maybe <code>null</code>, for audio only.
     * @throws IllegalStateException if not invoked in {@link State#Initialized}. 
     * @throws IllegalArgumentException if arguments are invalid
     * @throws StreamException forwarded from the off-thread stream initialization
     * @throws GLException in case of difficulties to initialize the GL resources
     */
    public void initGL(GL gl) throws IllegalStateException, IllegalArgumentException, StreamException, GLException;
    
    /** 
     * If implementation uses a {@link AudioSink}, it's instance will be returned.
     * <p> 
     * The {@link AudioSink} instance is available after {@link #initStream(URI, int, int, int)}, 
     * if used by implementation.
     * </p> 
     */
    public AudioSink getAudioSink();
    
    /**
     * Releases the GL and stream resources.
     * <p>
     * <a href="#lifecycle">Lifecycle</a>: <code>ANY</code> -> {@link State#Uninitialized}
     * </p>
     */
    public State destroy(GL gl);

    /**
     * Sets the playback speed.
     * <p>
     * Play speed is set to <i>normal</i>, i.e. <code>1.0f</code>
     * if <code> abs(1.0f - rate) < 0.01f</code> to simplify test.
     * </p>
     * @return true if successful, otherwise false, i.e. due to unsupported value range of implementation. 
     */
    public boolean setPlaySpeed(float rate);

    public float getPlaySpeed();

    /**
     * <a href="#lifecycle">Lifecycle</a>: {@link State#Paused} -> {@link State#Playing}
     */
    public State play();

    /**
     * <a href="#lifecycle">Lifecycle</a>: {@link State#Playing} -> {@link State#Paused}
     */
    public State pause();

    /**
     * Allowed in state {@link State#Playing} and {@link State#Paused}, otherwise ignored,
     * see <a href="#lifecycle">Lifecycle</a>. 
     * 
     * @param msec absolute desired time position in milliseconds 
     * @return time current position in milliseconds, after seeking to the desired position  
     **/
    public int seek(int msec);

    /**
     * See <a href="#lifecycle">Lifecycle</a>.
     * @return the current state, either {@link State#Uninitialized}, {@link State#Initialized}, {@link State#Playing} or {@link State#Paused}
     */
    public State getState();
    
    /**
     * Return the video stream id, see <a href="#streamIDs">audio and video Stream IDs</a>.
     */
    public int getVID();
    
    /**
     * Return the audio stream id, see <a href="#streamIDs">audio and video Stream IDs</a>.
     */
    public int getAID();
    
    /**
     * @return the current decoded frame count since {@link #play()} and {@link #seek(int)} 
     *         as increased by {@link #getNextTexture(GL)} or the decoding thread.
     */
    public int getDecodedFrameCount();
    
    /**
     * @return the current presented frame count since {@link #play()} and {@link #seek(int)} 
     *         as increased by {@link #getNextTexture(GL)} for new frames.
     */
    public int getPresentedFrameCount();
    
    /**
     * @return current video presentation timestamp (PTS) in milliseconds of {@link #getLastTexture()} 
     **/
    public int getVideoPTS();
    
    /**
     * @return current audio presentation timestamp (PTS) in milliseconds. 
     **/
    public int getAudioPTS();
    
    /**
     * {@inheritDoc}
     * <p>
     * See <a href="#synchronization">audio and video synchronization</a>.
     * </p>
     * @throws IllegalStateException if not invoked in {@link State#Paused} or {@link State#Playing}
     */
    @Override
    public TextureSequence.TextureFrame getLastTexture() throws IllegalStateException;

    /**
     * {@inheritDoc}
     * 
     * <p>
     * In case the current state is not {@link State#Playing}, {@link #getLastTexture()} is returned.
     * </p>
     * <p>
     * See <a href="#synchronization">audio and video synchronization</a>.
     * </p>
     * @throws IllegalStateException if not invoked in {@link State#Paused} or {@link State#Playing}
     * 
     * @see #addEventListener(GLMediaEventListener)
     * @see GLMediaEventListener#newFrameAvailable(GLMediaPlayer, TextureFrame, long)
     */
    @Override
    public TextureSequence.TextureFrame getNextTexture(GL gl) throws IllegalStateException;
    
    /** Return the stream location, as set by {@link #initStream(URI, int, int, int)}. */
    public URI getURI();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the code of the video stream, if available 
     */
    public String getVideoCodec();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the code of the audio stream, if available 
     */
    public String getAudioCodec();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the total number of video frames
     */
    public int getVideoFrames();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the total number of audio frames
     */
    public int getAudioFrames();

    /**
     * @return total duration of stream in msec.
     */
    public int getDuration();
    
    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the overall bitrate of the stream.  
     */
    public long getStreamBitrate();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return video bitrate  
     */
    public int getVideoBitrate();
    
    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the audio bitrate  
     */
    public int getAudioBitrate();
    
    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the framerate of the video
     */
    public float getFramerate();

    public int getWidth();

    public int getHeight();

    public String toString();

    public String getPerfString();
    
    public void addEventListener(GLMediaEventListener l);

    public void removeEventListener(GLMediaEventListener l);

    public GLMediaEventListener[] getEventListeners();    

}
