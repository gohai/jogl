/**
 * Copyright 2011 JogAmp Community. All rights reserved.
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

import com.jogamp.newt.Display;
import com.jogamp.newt.MonitorDevice;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.MonitorMode;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.util.MonitorModeUtil;
import com.jogamp.opengl.test.junit.jogl.demos.es2.GearsES2;
import com.jogamp.opengl.test.junit.util.UITestCase;
import java.util.List;
import javax.media.nativewindow.util.Dimension;

/**
 * Manual testing the ScreenImpl shutdown hook, 
 * which shall reset the ScreenMode to it's original state
 * when the application exists (normal or ctrl-c).
 */
public class ManualScreenMode03NEWT extends UITestCase {
    static int waitTime = 7000; // 1 sec
    
    static GLWindow createWindow(Screen screen, GLCapabilities caps, int width, int height, boolean onscreen, boolean undecorated) {
        caps.setOnscreen(onscreen);

        GLWindow window = GLWindow.create(screen, caps);
        window.setSize(width, height);
        window.addGLEventListener(new GearsES2());
        window.setVisible(true);
        return window;
    }

    public void run() {
        int width  = 640;
        int height = 480;
        GLProfile glp = GLProfile.getDefault();
        GLCapabilities caps = new GLCapabilities(glp);
        Display display = NewtFactory.createDisplay(null); // local display
        Screen screen  = NewtFactory.createScreen(display, 0); // screen 0
        GLWindow window = createWindow(screen, caps, width, height, true /* onscreen */, false /* undecorated */);

        List<MonitorMode> monitorModes = screen.getMonitorModes();
        if(null==monitorModes) {
            // no support ..
            System.err.println("Your platform has no ScreenMode change support, sorry");
            return;
        }
        Animator animator = new Animator(window);
        animator.start();

        MonitorDevice monitor = window.getMainMonitor();
        MonitorMode mmCurrent = monitor.queryCurrentMode();
        MonitorMode mmOrig = monitor.getOriginalMode();
        System.err.println("[0] orig   : "+mmOrig);
        System.err.println("[0] current: "+mmCurrent);

        monitorModes = MonitorModeUtil.filterByFlags(monitorModes, 0); // no interlace, double-scan etc
        monitorModes = MonitorModeUtil.filterByRotation(monitorModes, 0);
        monitorModes = MonitorModeUtil.filterByRate(monitorModes, mmOrig.getRefreshRate());
        monitorModes = MonitorModeUtil.filterByResolution(monitorModes, new Dimension(801, 601));
        monitorModes = MonitorModeUtil.getHighestAvailableBpp(monitorModes);

        MonitorMode mm = (MonitorMode) monitorModes.get(0);
        System.err.println("[0] set current: "+mm);
        monitor.setCurrentMode(mm);

        System.err.print("[0] post setting .. wait <");
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
        }
        System.err.println("done>");
        System.exit(0);
    }

    public static void main(String args[]) throws IOException {
        ManualScreenMode03NEWT t = new ManualScreenMode03NEWT();
        t.run();
    }
}
