/**
 * Copyright 2010 JogAmp Community. All rights reserved.
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
 
package com.jogamp.opengl.test.junit.newt;

import java.io.IOException;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLProfile;

import com.jogamp.opengl.util.Animator;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jogamp.newt.Display;
import com.jogamp.newt.MonitorDevice;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.Window;
import com.jogamp.newt.MonitorMode;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.util.MonitorModeUtil;
import com.jogamp.opengl.test.junit.jogl.demos.es2.GearsES2;
import com.jogamp.opengl.test.junit.util.AWTRobotUtil;
import com.jogamp.opengl.test.junit.util.UITestCase;

import java.util.List;
import javax.media.nativewindow.util.Dimension;

/**
 * Demonstrates fullscreen with and without ScreenMode change.
 * 
 * <p>
 * Also documents NV RANDR/GL bug, see {@link TestScreenMode01NEWT#cleanupGL()}.</p> 
 */
public class TestScreenMode01NEWT extends UITestCase {
    static GLProfile glp;
    static int width, height;
    
    static int waitTimeShort = 2000; // 2 sec
    static int waitTimeLong = 8000; // 8 sec

    @BeforeClass
    public static void initClass() {
        width  = 640;
        height = 480;
        glp = GLProfile.getDefault();
    }

    @AfterClass
    public static void releaseClass() throws InterruptedException {
        Thread.sleep(waitTimeShort);
    }
    
    /**
     * Following configurations results in a SIGSEGV:
     * <pre>
     *   Ubuntu 11.04 (natty), NV GTX 460, driver [280.10* - 285.03]
     * </pre>
     * 
     * Situation:
     * <pre>
     *   1 - Create Screen, GLWindow (w/ context)
     *   2 - ScreenMode change
     *   3 - Destroy GLWindow (w/ context), Screen
     *   4 - Create  Screen, GLWindow (w/ context) (*)
     * </pre>
     *   
     * Step 4 causes the exception within 1st 'glXMakeContextCurrent(..)' call
     * on the the created GL context.
     * 
     * Remedy:
     * <pre>
     *   A) Releasing all resources before step 4 .. works.
     *   B) Holding the native Display/Screen in NEWT also works (ie screen.addReference()).
     * </pre>
     * 
     * Hence there must be some correlations with the screen randr mode
     * and some of the glcontext/gldrawables.
     * 
     * <pre>
     * Remedy A) is demonstrated here
     * Remedy B) is shown in {@link TestScreenMode01bNEWT}
     * </pre>
     */
    private void cleanupGL() throws InterruptedException {
        System.err.println("*** cleanupGL.shutdown");
        GLProfile.shutdown();
        System.err.println("*** cleanupGL.initSingleton");
        GLProfile.initSingleton();
        System.err.println("*** cleanupGL.DONE");
    }
    
    static GLWindow createWindow(Screen screen, GLCapabilities caps, int width, int height, boolean onscreen, boolean undecorated) {
        Assert.assertNotNull(caps);
        caps.setOnscreen(onscreen);

        GLWindow window = GLWindow.create(screen, caps);
        window.setSize(width, height);
        window.addGLEventListener(new GearsES2());
        Assert.assertNotNull(window);
        window.setVisible(true);
        return window;
    }

    static void destroyWindow(Window window) {
        if(null!=window) {
            window.destroy();
        }
    }
    
    @Test
    public void testFullscreenChange01() throws InterruptedException {
        Thread.sleep(waitTimeShort);
        GLCapabilities caps = new GLCapabilities(glp);
        Assert.assertNotNull(caps);
        Display display = NewtFactory.createDisplay(null); // local display
        Assert.assertNotNull(display);
        Screen screen  = NewtFactory.createScreen(display, 0); // screen 0
        Assert.assertNotNull(screen);

        GLWindow window = createWindow(screen, caps, width, height, true /* onscreen */, false /* undecorated */);
        Animator animator = new Animator(window);
        animator.start();
        
        final MonitorDevice monitor = window.getMainMonitor();
        
        Assert.assertEquals(false, window.isFullscreen());
        Assert.assertEquals(width, window.getWidth());
        Assert.assertEquals(height, window.getHeight());
        
        window.setFullscreen(true);
        Assert.assertEquals(true, window.isFullscreen());        
        Assert.assertEquals(monitor.getViewport().getWidth(), window.getWidth());
        Assert.assertEquals(monitor.getViewport().getHeight(), window.getHeight());
        
        Thread.sleep(waitTimeShort);

        window.setFullscreen(false);
        Assert.assertEquals(false, window.isFullscreen());
        Assert.assertEquals(width, window.getWidth());
        Assert.assertEquals(height, window.getHeight());
        
        Thread.sleep(waitTimeShort);

        animator.stop();
        Assert.assertEquals(false, animator.isAnimating());
        Assert.assertEquals(false, animator.isStarted());
        
        destroyWindow(window);
        Assert.assertTrue(AWTRobotUtil.waitForRealized(window, false));
        Assert.assertEquals(false, window.isRealized());
        Assert.assertEquals(false, window.isNativeValid());
        
        cleanupGL();
    }

    @Test
    public void testScreenModeChange01() throws InterruptedException {
        Thread.sleep(waitTimeShort);

        GLCapabilities caps = new GLCapabilities(glp);
        Assert.assertNotNull(caps);
        Display display = NewtFactory.createDisplay(null); // local display
        Assert.assertNotNull(display);
        Screen screen  = NewtFactory.createScreen(display, 0); // screen 0
        Assert.assertNotNull(screen);
        GLWindow window = createWindow(screen, caps, width, height, true /* onscreen */, false /* undecorated */);
        Assert.assertNotNull(window);

        MonitorDevice monitor = window.getMainMonitor();
        
        List<MonitorMode> monitorModes = monitor.getSupportedModes();
        Assert.assertTrue(monitorModes.size()>0);
        if(monitorModes.size()==1) {
            // no support ..
            System.err.println("Your platform has no MonitorMode change support, sorry");
            destroyWindow(window);
            return;
        }

        Animator animator = new Animator(window);
        animator.start();

        MonitorMode mmCurrent = monitor.queryCurrentMode();
        Assert.assertNotNull(mmCurrent);
        MonitorMode mmOrig = monitor.getOriginalMode();
        Assert.assertNotNull(mmOrig);
        System.err.println("[0] orig   : "+mmOrig);
        System.err.println("[0] current: "+mmCurrent);
        Assert.assertEquals(mmCurrent, mmOrig);

        monitorModes = MonitorModeUtil.filterByFlags(monitorModes, 0); // no interlace, double-scan etc
        Assert.assertNotNull(monitorModes);
        Assert.assertTrue(monitorModes.size()>0);
        monitorModes = MonitorModeUtil.filterByRotation(monitorModes, 0);
        Assert.assertNotNull(monitorModes);
        Assert.assertTrue(monitorModes.size()>0);
        monitorModes = MonitorModeUtil.filterByRate(monitorModes, mmOrig.getRefreshRate());
        Assert.assertNotNull(monitorModes);
        Assert.assertTrue(monitorModes.size()>0);
        monitorModes = MonitorModeUtil.filterByResolution(monitorModes, new Dimension(801, 601));
        Assert.assertNotNull(monitorModes);
        Assert.assertTrue(monitorModes.size()>0);
        
        monitorModes = MonitorModeUtil.getHighestAvailableBpp(monitorModes);
        Assert.assertNotNull(monitorModes);
        Assert.assertTrue(monitorModes.size()>0);

        MonitorMode sm = (MonitorMode) monitorModes.get(0);
        System.err.println("[0] set current: "+sm);
        monitor.setCurrentMode(sm);
        Assert.assertTrue(monitor.isModeChangedByUs());
        Assert.assertEquals(sm, monitor.getCurrentMode());
        Assert.assertNotSame(mmOrig, monitor.getCurrentMode());
        Assert.assertEquals(sm, monitor.queryCurrentMode());        
        
        Thread.sleep(waitTimeLong);

        // check reset ..

        Assert.assertEquals(true,display.isNativeValid());
        Assert.assertEquals(true,screen.isNativeValid());
        Assert.assertEquals(true,window.isNativeValid());
        Assert.assertEquals(true,window.isVisible());

        animator.stop();        
        Assert.assertEquals(false, animator.isAnimating());
        Assert.assertEquals(false, animator.isStarted());
        
        destroyWindow(window);
        Assert.assertTrue(AWTRobotUtil.waitForRealized(window, false));

        Assert.assertEquals(false,window.isVisible());
        Assert.assertEquals(false,window.isRealized());
        Assert.assertEquals(false,window.isNativeValid());
        Assert.assertEquals(false,screen.isNativeValid());
        Assert.assertEquals(false,display.isNativeValid());

        screen.createNative(); // trigger native re-creation

        Assert.assertEquals(true,display.isNativeValid());
        Assert.assertEquals(true,screen.isNativeValid());

        mmCurrent = monitor.getCurrentMode();
        System.err.println("[1] current/orig: "+mmCurrent);
        screen.destroy();
        Assert.assertEquals(false,screen.isNativeValid());
        Assert.assertEquals(false,display.isNativeValid());

        Assert.assertNotNull(mmCurrent);
        Assert.assertEquals(mmCurrent, mmOrig);
        
        cleanupGL();
    }

    @Test
    public void testScreenModeChangeWithFS01Pre() throws InterruptedException {
        Thread.sleep(waitTimeShort);
        testScreenModeChangeWithFS01Impl(true) ;
    }

    @Test
    public void testScreenModeChangeWithFS01Post() throws InterruptedException {
        Thread.sleep(waitTimeShort);
        testScreenModeChangeWithFS01Impl(false) ;
    }

    protected void testScreenModeChangeWithFS01Impl(boolean preFS) throws InterruptedException {
        GLCapabilities caps = new GLCapabilities(glp);
        Display display = NewtFactory.createDisplay(null); // local display
        Screen screen  = NewtFactory.createScreen(display, 0); // screen 0
        GLWindow window = createWindow(screen, caps, width, height, true /* onscreen */, false /* undecorated */);
        Animator animator = new Animator(window);
        animator.start();

        MonitorDevice monitor = window.getMainMonitor();
        MonitorMode mmCurrent = monitor.queryCurrentMode();
        Assert.assertNotNull(mmCurrent);
        MonitorMode mmOrig = monitor.getOriginalMode();
        Assert.assertNotNull(mmOrig);
        System.err.println("[0] orig   : "+mmOrig);
        System.err.println("[0] current: "+mmCurrent);
        Assert.assertEquals(mmCurrent, mmOrig);
        
        List<MonitorMode> monitorModes = monitor.getSupportedModes();
        if(monitorModes.size()==1) {
            // no support ..
            destroyWindow(window);
            return;
        }
        Assert.assertTrue(monitorModes.size()>0);
        monitorModes = MonitorModeUtil.filterByFlags(monitorModes, 0); // no interlace, double-scan etc
        monitorModes = MonitorModeUtil.filterByRotation(monitorModes, 0);
        monitorModes = MonitorModeUtil.filterByRate(monitorModes, mmOrig.getRefreshRate());
        monitorModes = MonitorModeUtil.filterByResolution(monitorModes, new Dimension(801, 601));
        monitorModes = MonitorModeUtil.getHighestAvailableBpp(monitorModes);

        MonitorMode monitorMode = (MonitorMode) monitorModes.get(0);
        Assert.assertNotNull(monitorMode);
        
        if(preFS) {
            System.err.println("[0] set FS pre 0: "+window.isFullscreen());
            window.setFullscreen(true);
            System.err.println("[0] set FS pre 1: "+window.isFullscreen());
            Assert.assertEquals(true, window.isFullscreen());
            System.err.println("[0] set FS pre X: "+window.isFullscreen());
        }

        System.err.println("[0] set current: "+monitorMode);
        monitor.setCurrentMode(monitorMode);
        
        if(!preFS) {
            System.err.println("[0] set FS post 0: "+window.isFullscreen());
            window.setFullscreen(true);
            Assert.assertEquals(true, window.isFullscreen());
            System.err.println("[0] set FS post X: "+window.isFullscreen());
        }

        Thread.sleep(waitTimeLong);
        
        // check reset ..

        Assert.assertEquals(true,display.isNativeValid());
        Assert.assertEquals(true,screen.isNativeValid());
        Assert.assertEquals(true,window.isNativeValid());
        Assert.assertEquals(true,window.isVisible());

        animator.stop();
        Assert.assertEquals(false, animator.isAnimating());
        Assert.assertEquals(false, animator.isStarted());
        
        destroyWindow(window);
        Assert.assertTrue(AWTRobotUtil.waitForRealized(window, false));

        Assert.assertEquals(false,window.isVisible());
        Assert.assertEquals(false,window.isRealized());
        Assert.assertEquals(false,window.isNativeValid());
        Assert.assertEquals(false,screen.isNativeValid());
        Assert.assertEquals(false,display.isNativeValid());

        screen.createNative(); // trigger native re-creation

        Assert.assertEquals(true,display.isNativeValid());
        Assert.assertEquals(true,screen.isNativeValid());
        
        mmCurrent = monitor.getCurrentMode();
        System.err.println("[1] current/orig: "+mmCurrent);
        screen.destroy();
        Assert.assertEquals(false,screen.isNativeValid());
        Assert.assertEquals(false,display.isNativeValid());

        Assert.assertNotNull(mmCurrent);
        Assert.assertEquals(mmCurrent, mmOrig);
        
        cleanupGL();
    }

    public static void main(String args[]) throws IOException {
        String tstname = TestScreenMode01NEWT.class.getName();
        org.junit.runner.JUnitCore.main(tstname);
    }
}
