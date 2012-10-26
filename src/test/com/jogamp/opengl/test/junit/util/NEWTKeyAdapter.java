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
 
package com.jogamp.opengl.test.junit.util;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import com.jogamp.newt.event.InputEvent;
import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;

public class NEWTKeyAdapter extends KeyAdapter implements KeyEventCountAdapter {

    String prefix;
    int keyPressed, keyReleased, keyTyped;
    int keyPressedAR, keyReleasedAR, keyTypedAR;
    boolean pressed;
    List<EventObject> queue = new ArrayList<EventObject>();
    boolean verbose = true;

    public NEWTKeyAdapter(String prefix) {
        this.prefix = prefix;
        reset();
    }
    
    public void setVerbose(boolean v) { verbose = false; }

    public boolean isPressed() {
        return pressed;
    }
    
    public int getCount() {
        return keyTyped;
    }

    public int getKeyPressedCount(boolean autoRepeatOnly) {
        return autoRepeatOnly ? keyPressedAR: keyPressed; 
    }
    
    public int getKeyReleasedCount(boolean autoRepeatOnly) {
        return autoRepeatOnly ? keyReleasedAR: keyReleased; 
    }
    
    public int getKeyTypedCount(boolean autoRepeatOnly) {
        return autoRepeatOnly ? keyTypedAR: keyTyped; 
    }
    
    public List<EventObject> getQueued() {
        return queue;
    }
    
    public void reset() {
        keyTyped = 0;
        keyPressed = 0;
        keyReleased = 0;
        keyTypedAR = 0;
        keyPressedAR = 0;
        keyReleasedAR = 0;
        pressed = false;
        queue.clear();
    }

    public void keyPressed(KeyEvent e) {
        pressed = true;
        keyPressed++;
        if( 0 != ( e.getModifiers() & InputEvent.AUTOREPEAT_MASK ) ) {
            keyPressedAR++;
        }
        queue.add(e);
        if( verbose ) {
            System.err.println("NEWT AWT PRESSED ["+pressed+"]: "+prefix+", "+e);
        }
    }
    
    public void keyReleased(KeyEvent e) {
        pressed = false;
        keyReleased++;
        if( 0 != ( e.getModifiers() & InputEvent.AUTOREPEAT_MASK ) ) {
            keyReleasedAR++;
        }
        queue.add(e);
        if( verbose ) {
            System.err.println("NEWT AWT RELEASED ["+pressed+"]: "+prefix+", "+e);
        }
    }
     
    @Override
    public void keyTyped(KeyEvent e) {
        keyTyped++;
        if( 0 != ( e.getModifiers() & InputEvent.AUTOREPEAT_MASK ) ) {
            keyTypedAR++;
        }
        queue.add(e);
        if( verbose ) {
            System.err.println("KEY NEWT TYPED ["+keyTyped+"]: "+prefix+", "+e);
        }
    }
    
    public String toString() { return prefix+"[pressed "+pressed+", typed "+keyTyped+"]"; }
}

