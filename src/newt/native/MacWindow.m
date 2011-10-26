/*
 * Copyright (c) 2009 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 */

#import <inttypes.h>

#import "jogamp_newt_driver_macosx_MacWindow.h"
#import "NewtMacWindow.h"

#import "MouseEvent.h"
#import "KeyEvent.h"

#import <ApplicationServices/ApplicationServices.h>

#import <stdio.h>

static const char * const ClazzNamePoint = "javax/media/nativewindow/util/Point";
static const char * const ClazzAnyCstrName = "<init>";
static const char * const ClazzNamePointCstrSignature = "(II)V";
static jclass pointClz = NULL;
static jmethodID pointCstr = NULL;
static jmethodID focusActionID = NULL;

static NSString* jstringToNSString(JNIEnv* env, jstring jstr)
{
    const jchar* jstrChars = (*env)->GetStringChars(env, jstr, NULL);
    NSString* str = [[NSString alloc] initWithCharacters: jstrChars length: (*env)->GetStringLength(env, jstr)];
    (*env)->ReleaseStringChars(env, jstr, jstrChars);
    return str;
}

static void setFrameTopLeftPoint(NSWindow* pWin, NewtMacWindow* mWin, jint x, jint y) {
    NSPoint pS = [mWin newtScreenWinPos2OSXScreenPos: NSMakePoint(x, y)];
    [mWin setFrameOrigin: pS];

    NSView* mView = [mWin contentView];
    [mWin invalidateCursorRectsForView: mView];
}

static NewtView * changeContentView(JNIEnv *env, jobject javaWindowObject, NSWindow *pwin, NSView *pview, NewtMacWindow *win, NewtView *newView) {
    NSView* oldNSView = [win contentView];
    NewtView* oldView = NULL;
#ifdef VERBOSE_ON
    int dbgIdx = 1;
#endif

    DBG_PRINT( "changeContentView.%d win %p, view %p, parent[win %p, view %p]\n", dbgIdx++, win, newView, pwin, pview);

    if(NULL!=oldNSView) {
NS_DURING
        // Available >= 10.5 - Makes the menubar disapear
        if([oldNSView isInFullScreenMode]) {
            [oldNSView exitFullScreenModeWithOptions: NULL];
        }
NS_HANDLER
NS_ENDHANDLER
        if( [oldNSView isMemberOfClass:[NewtView class]] ) {
            oldView = (NewtView *) oldNSView;

            jobject globJavaWindowObject = [oldView getJavaWindowObject];
            (*env)->DeleteGlobalRef(env, globJavaWindowObject);
            [oldView setJavaWindowObject: NULL];
            [oldView setDestroyNotifySent: false];
        }
        if(NULL!=pwin) {
            [oldView removeFromSuperview];
        }
    }
    DBG_PRINT( "changeContentView.%d isHidden %d, isHiddenOrHasHiddenAncestor: %d\n", dbgIdx++, 
        [newView isHidden], [newView isHiddenOrHasHiddenAncestor]);

    if(NULL!=newView) {
        jobject globJavaWindowObject = (*env)->NewGlobalRef(env, javaWindowObject);
        [newView setJavaWindowObject: globJavaWindowObject];
        [newView setDestroyNotifySent: false];
        {
            JavaVM *jvmHandle = NULL;
            int jvmVersion = 0;

            if(0 != (*env)->GetJavaVM(env, &jvmHandle)) {
                jvmHandle = NULL;
            } else {
                jvmVersion = (*env)->GetVersion(env);
            }
            [newView setJVMHandle: jvmHandle];
            [newView setJVMVersion: jvmVersion];
        }

        DBG_PRINT( "changeContentView.%d\n", dbgIdx++);

        if(NULL!=pwin) {
            [pview addSubview: newView positioned: NSWindowAbove relativeTo: nil];
        }
    }
    DBG_PRINT( "changeContentView.%d isHidden %d, isHiddenOrHasHiddenAncestor: %d\n", dbgIdx++, 
        [newView isHidden], [newView isHiddenOrHasHiddenAncestor]);

    [win setContentView: newView];
    DBG_PRINT( "changeContentView.%d\n", dbgIdx++);

    // make sure the insets are updated in the java object
    [win updateInsets: env];
    DBG_PRINT( "changeContentView.%d\n", dbgIdx++);

    return oldView;
}

/*
 * Class:     jogamp_newt_driver_macosx_MacDisplay
 * Method:    initIDs
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_macosx_MacDisplay_initNSApplication0
  (JNIEnv *env, jclass clazz)
{
    static int initialized = 0;

    if(initialized) return JNI_TRUE;
    initialized = 1;

    // This little bit of magic is needed in order to receive mouse
    // motion events and allow key focus to be properly transferred.
    // FIXME: are these Carbon APIs? They come from the
    // ApplicationServices.framework.
    ProcessSerialNumber psn;
    if (GetCurrentProcess(&psn) == noErr) {
        TransformProcessType(&psn, kProcessTransformToForegroundApplication);
        SetFrontProcess(&psn);
    }

    // Initialize the shared NSApplication instance
    [NSApplication sharedApplication];

    // Need this when debugging, as it is necessary to attach gdb to
    // the running java process -- "gdb java" doesn't work
    //    printf("Going to sleep for 10 seconds\n");
    //    sleep(10);

    return (jboolean) JNI_TRUE;
}

/*
 * Class:     jogamp_newt_driver_macosx_MacDisplay
 * Method:    runNSApplication0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacDisplay_runNSApplication0
  (JNIEnv *env, jclass clazz)
{
    // NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    DBG_PRINT( "\nrunNSApplication0.0\n");

    [NSApp run];

    DBG_PRINT( "\nrunNSApplication0.X\n");
    // [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacScreen
 * Method:    getWidthImpl
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_jogamp_newt_driver_macosx_MacScreen_getWidthImpl0
  (JNIEnv *env, jclass clazz, jint screen_idx)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];

    NSArray *screens = [NSScreen screens];
    if(screen_idx<0) screen_idx=0;
    if(screen_idx>=[screens count]) screen_idx=0;
    NSScreen *screen = (NSScreen *) [screens objectAtIndex: screen_idx];
    NSRect rect = [screen frame];

    [pool release];

    return (jint) (rect.size.width);
}

/*
 * Class:     jogamp_newt_driver_macosx_MacScreen
 * Method:    getHeightImpl
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_jogamp_newt_driver_macosx_MacScreen_getHeightImpl0
  (JNIEnv *env, jclass clazz, jint screen_idx)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];

    NSArray *screens = [NSScreen screens];
    if(screen_idx<0) screen_idx=0;
    if(screen_idx>=[screens count]) screen_idx=0;
    NSScreen *screen = (NSScreen *) [screens objectAtIndex: screen_idx];
    NSRect rect = [screen frame];

    [pool release];

    return (jint) (rect.size.height);
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    initIDs
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_macosx_MacWindow_initIDs0
  (JNIEnv *env, jclass clazz)
{
    static int initialized = 0;

    if(initialized) return JNI_TRUE;
    initialized = 1;

    jclass c;
    c = (*env)->FindClass(env, ClazzNamePoint);
    if(NULL==c) {
        NewtCommon_FatalError(env, "FatalError Java_jogamp_newt_driver_macosx_MacWindow_initIDs0: can't find %s", ClazzNamePoint);
    }
    pointClz = (jclass)(*env)->NewGlobalRef(env, c);
    (*env)->DeleteLocalRef(env, c);
    if(NULL==pointClz) {
        NewtCommon_FatalError(env, "FatalError Java_jogamp_newt_driver_macosx_MacWindow_initIDs0: can't use %s", ClazzNamePoint);
    }
    pointCstr = (*env)->GetMethodID(env, pointClz, ClazzAnyCstrName, ClazzNamePointCstrSignature);
    if(NULL==pointCstr) {
        NewtCommon_FatalError(env, "FatalError Java_jogamp_newt_driver_macosx_MacWindow_initIDs0: can't fetch %s.%s %s",
            ClazzNamePoint, ClazzAnyCstrName, ClazzNamePointCstrSignature);
    }

    focusActionID = (*env)->GetMethodID(env, clazz, "focusAction", "()Z");
    if(NULL==focusActionID) {
        NewtCommon_FatalError(env, "FatalError Java_jogamp_newt_driver_macosx_MacWindow_initIDs0: can't fetch method focusAction()Z");
    }

    // Need this when debugging, as it is necessary to attach gdb to
    // the running java process -- "gdb java" doesn't work
    //    printf("Going to sleep for 10 seconds\n");
    //    sleep(10);

    return (jboolean) [NewtMacWindow initNatives: env forClass: clazz];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    createWindow0
 * Signature: (JIIIIZIIIJ)J
 */
JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_macosx_MacWindow_createWindow0
  (JNIEnv *env, jobject jthis, jlong parent, jint x, jint y, jint w, jint h, jboolean opaque, jboolean fullscreen, jint styleMask, 
   jint bufferingType, jint screen_idx, jlong jview)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NewtView* myView = (NewtView*) (intptr_t) jview ;

    DBG_PRINT( "createWindow0 - %p (this), %p (parent), %d/%d %dx%d, opaque %d, fs %d, style %X, buffType %X, screenidx %d, view %p (START)\n",
        (void*)(intptr_t)jthis, (void*)(intptr_t)parent, (int)x, (int)y, (int)w, (int)h, (int) opaque, (int)fullscreen, 
        (int)styleMask, (int)bufferingType, (int)screen_idx, myView);

    NSArray *screens = [NSScreen screens];
    if(screen_idx<0) screen_idx=0;
    if(screen_idx>=[screens count]) screen_idx=0;
    NSScreen *myScreen = (NSScreen *) [screens objectAtIndex: screen_idx];
    NSRect rect;

    if (fullscreen) {
        styleMask = NSBorderlessWindowMask;
        rect = [myScreen frame];
        x = 0;
        y = 0;
        w = (jint) (rect.size.width);
        h = (jint) (rect.size.height);
    } else {
        rect = NSMakeRect(x, y, w, h);
    }

    // Allocate the window
    NewtMacWindow* myWindow = [[NewtMacWindow alloc] initWithContentRect: rect
                                               styleMask: (NSUInteger) styleMask
                                               backing: (NSBackingStoreType) bufferingType
                                               defer: NO
                                               screen: myScreen];
    [myWindow setReleasedWhenClosed: YES]; // default
    [myWindow setPreservesContentDuringLiveResize: NO];

    NSObject *nsParentObj = (NSObject*) ((intptr_t) parent);
    NSWindow* parentWindow = NULL;
    NSView* parentView = NULL;
    if( nsParentObj != NULL && [nsParentObj isKindOfClass:[NSWindow class]] ) {
        parentWindow = (NSWindow*) nsParentObj;
        parentView = [parentWindow contentView];
        DBG_PRINT( "createWindow0 - Parent is NSWindow : %p (view) -> %p (win) \n", parentView, parentWindow);
    } else if( nsParentObj != NULL && [nsParentObj isKindOfClass:[NSView class]] ) {
        parentView = (NSView*) nsParentObj;
        parentWindow = [parentView window];
        DBG_PRINT( "createWindow0 - Parent is NSView : %p -(view) > %p (win) \n", parentView, parentWindow);
    } else {
        DBG_PRINT( "createWindow0 - Parent is neither NSWindow nor NSView : %p\n", nsParentObj);
    }
    DBG_PRINT( "createWindow0 - is visible.1: %d\n", [myWindow isVisible]);

#ifdef VERBOSE_ON
    int dbgIdx = 1;
#endif
    if(opaque) {
        [myWindow setOpaque: YES];
        DBG_PRINT( "createWindow0.%d\n", dbgIdx++);
        if (!fullscreen) {
            [myWindow setShowsResizeIndicator: YES];
        }
        DBG_PRINT( "createWindow0.%d\n", dbgIdx++);
    } else {
        [myWindow setOpaque: NO];
        [myWindow setBackgroundColor: [NSColor clearColor]];
    }

    // specify we want mouse-moved events
    [myWindow setAcceptsMouseMovedEvents:YES];
    DBG_PRINT( "createWindow0.%d\n", dbgIdx++);

    // Use given NewtView or allocate an NewtView if NULL
    if(NULL == myView) {
        myView = [[NewtView alloc] initWithFrame: rect] ;
        DBG_PRINT( "createWindow0 - new own view: %p\n", myView);
    } else {
        DBG_PRINT( "createWindow0 - use given view: %p\n", myView);
    }

    DBG_PRINT( "createWindow0 - is visible.%d: %d\n", dbgIdx++, [myWindow isVisible]);

    // Set the content view
    (void) changeContentView(env, jthis, parentWindow, parentView, myWindow, myView);

    DBG_PRINT( "createWindow0.%d\n", dbgIdx++);

    if(NULL!=parentWindow) {
        [myWindow attachToParent: parentWindow];
    }

    // Immediately re-position the window based on an upper-left coordinate system
    setFrameTopLeftPoint(parentWindow, myWindow, x, y);

    // force surface creation (causes an AWT parent to fail .. some times)
    // [myView lockFocus];
    // [myView unlockFocus];

    // concurrent view rendering
    [myWindow setAllowsConcurrentViewDrawing: YES];
    [myView setCanDrawConcurrently: YES];

    // visible on front
    [myWindow orderFront: myWindow];

NS_DURING
    // Available >= 10.5 - Makes the menubar disapear
    if(fullscreen) {
         [myView enterFullScreenMode: myScreen withOptions:NULL];
    }
NS_HANDLER
NS_ENDHANDLER

    // Set the next responder to be the window so that we can forward
    // right mouse button down events
    [myView setNextResponder: myWindow];

    DBG_PRINT( "createWindow0 - %p (this), %p (parent): new window: %p (END)\n",
        (void*)(intptr_t)jthis, (void*)(intptr_t)parent, myWindow);

    [pool release];

    return (jlong) ((intptr_t) myWindow);
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    close0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_close0
  (JNIEnv *env, jobject unused, jlong window)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NewtMacWindow* mWin = (NewtMacWindow*) ((intptr_t) window);
    NSView* mView = [mWin contentView];
    NSWindow* pWin = [mWin parentWindow];
    DBG_PRINT( "*************** windowClose.0: %p (view %p, parent %p)\n", mWin, mView, pWin);
NS_DURING
    if(NULL!=mView) {
        // Available >= 10.5 - Makes the menubar disapear
        if([mView isInFullScreenMode]) {
            [mView exitFullScreenModeWithOptions: NULL];
        }
        [mWin setContentView: nil];
        [mView release];
    }
NS_HANDLER
NS_ENDHANDLER

    if(NULL!=pWin) {
        [mWin detachFromParent: pWin];
    }
    [mWin orderOut: mWin];

    [mWin close]; // performs release!

    DBG_PRINT( "*************** windowClose.X: %p (parent %p)\n", mWin, pWin);

    [pool release];
}

/*
 * Class:     Java_jogamp_newt_driver_macosx_MacWindow
 * Method:    lockSurface0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_macosx_MacWindow_lockSurface0
  (JNIEnv *env, jclass clazz, jlong window)
{
    NewtMacWindow *mWin = (NewtMacWindow*) ((intptr_t) window);
    NSView * mView = [mWin contentView];
    return [mView canDraw] == YES ? JNI_TRUE : JNI_FALSE;
    // return [mView lockFocusIfCanDraw] == YES ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     Java_jogamp_newt_driver_macosx_MacWindow
 * Method:    unlockSurface0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_unlockSurface0
  (JNIEnv *env, jclass clazz, jlong window)
{
    /** deadlocks, since we render independent of focus
    NewtMacWindow *mWin = (NewtMacWindow*) ((intptr_t) window);
    NSView * mView = [mWin contentView];
    [mView unlockFocus]; */
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    requestFocus0
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_requestFocus0
  (JNIEnv *env, jobject window, jlong w, jboolean force)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* win = (NSWindow*) ((intptr_t) w);
#ifdef VERBOSE_ON
    BOOL hasFocus = [win isKeyWindow];
#endif

    DBG_PRINT( "requestFocus - window: %p, force %d, hasFocus %d (START)\n", win, force, hasFocus);

    // Even if we already own the focus, we need the 'focusAction()' call
    // and the other probably redundant NS calls to force proper focus traversal 
    // of the parent TK (AWT doesn't do it properly on OSX).
    if( JNI_TRUE==force || JNI_FALSE == (*env)->CallBooleanMethod(env, window, focusActionID) ) {
        DBG_PRINT( "makeKeyWindow win %p\n", win);
        // [win performSelectorOnMainThread:@selector(orderFrontRegardless) withObject:nil waitUntilDone:YES];
        // [win performSelectorOnMainThread:@selector(makeKeyWindow) withObject:nil waitUntilDone:YES];
        [win orderFrontRegardless];
        [win makeKeyWindow];
        [win makeFirstResponder: nil];
    }

    DBG_PRINT( "requestFocus - window: %p, force %d (END)\n", win, force);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    orderFront0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_orderFront0
  (JNIEnv *env, jobject unused, jlong window)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* win = (NSWindow*) ((intptr_t) window);

    DBG_PRINT( "orderFront0 - window: %p (START)\n", win);

    [win orderFrontRegardless];

    DBG_PRINT( "orderFront0 - window: %p (END)\n", win);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    orderOut
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_orderOut0
  (JNIEnv *env, jobject unused, jlong window)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* mWin = (NSWindow*) ((intptr_t) window);
    NSWindow* pWin = [mWin parentWindow];

    DBG_PRINT( "orderOut0 - window: (parent %p) %p (START)\n", pWin, mWin);

    if(NULL == pWin) {
        [mWin orderOut: mWin];
    } else {
        [mWin orderBack: mWin];
    }

    DBG_PRINT( "orderOut0 - window: (parent %p) %p (END)\n", pWin, mWin);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    setTitle0
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_setTitle0
  (JNIEnv *env, jobject unused, jlong window, jstring title)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* win = (NSWindow*) ((intptr_t) window);

    DBG_PRINT( "setTitle0 - window: %p (START)\n", win);

    NSString* str = jstringToNSString(env, title);
    [str autorelease];
    [win setTitle: str];

    DBG_PRINT( "setTitle0 - window: %p (END)\n", win);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    contentView
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_macosx_MacWindow_contentView0
  (JNIEnv *env, jobject unused, jlong window)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* win = (NSWindow*) ((intptr_t) window);

    DBG_PRINT( "contentView0 - window: %p (START)\n", win);

    jlong res = (jlong) ((intptr_t) [win contentView]);

    DBG_PRINT( "contentView0 - window: %p (END)\n", win);

    [pool release];
    return res;
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    changeContentView
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_macosx_MacWindow_changeContentView0
  (JNIEnv *env, jobject jthis, jlong parentWindowOrView, jlong window, jlong jview)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];

    NewtMacWindow* win = (NewtMacWindow*) ((intptr_t) window);
    NewtView* newView = (NewtView *) ((intptr_t) jview);

    DBG_PRINT( "changeContentView0 - window: %p (START)\n", win);

    NSObject *nsParentObj = (NSObject*) ((intptr_t) parentWindowOrView);
    NSWindow* pWin = NULL;
    NSView* pView = NULL;
    if( NULL != nsParentObj ) {
        if( [nsParentObj isKindOfClass:[NSWindow class]] ) {
            pWin = (NSWindow*) nsParentObj;
            pView = [pWin contentView];
        } else if( [nsParentObj isKindOfClass:[NSView class]] ) {
            pView = (NSView*) nsParentObj;
            pWin = [pView window];
        }
    }

    NewtView* oldView = changeContentView(env, jthis, pWin, pView, win, newView);

    DBG_PRINT( "changeContentView0 - window: %p (END)\n", win);

    [pool release];

    return (jlong) ((intptr_t) oldView);
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    setContentSize
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_setContentSize0
  (JNIEnv *env, jobject unused, jlong window, jint w, jint h)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* win = (NSWindow*) ((intptr_t) window);

    DBG_PRINT( "setContentSize0 - window: %p (START)\n", win);

    NSSize sz = NSMakeSize(w, h);
    [win setContentSize: sz];

    DBG_PRINT( "setContentSize0 - window: %p (END)\n", win);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    setFrameTopLeftPoint
 * Signature: (JJII)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_setFrameTopLeftPoint0
  (JNIEnv *env, jobject unused, jlong parent, jlong window, jint x, jint y)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NewtMacWindow* mWin = (NewtMacWindow*) ((intptr_t) window);

    NSObject *nsParentObj = (NSObject*) ((intptr_t) parent);
    NSWindow* pWin = NULL;
    if( nsParentObj != NULL && [nsParentObj isKindOfClass:[NSWindow class]] ) {
        pWin = (NSWindow*) nsParentObj;
    } else if( nsParentObj != NULL && [nsParentObj isKindOfClass:[NSView class]] ) {
        NSView* pView = (NSView*) nsParentObj;
        pWin = [pView window];
    }

    DBG_PRINT( "setFrameTopLeftPoint0 - window: %p, parent %p (START)\n", mWin, pWin);

    setFrameTopLeftPoint(pWin, mWin, x, y);

    DBG_PRINT( "setFrameTopLeftPoint0 - window: %p, parent %p (END)\n", mWin, pWin);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    setAlwaysOnTop0
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_setAlwaysOnTop0
  (JNIEnv *env, jobject unused, jlong window, jboolean atop)
{
    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSWindow* win = (NSWindow*) ((intptr_t) window);

    DBG_PRINT( "setAlwaysOnTop0 - window: %p (START)\n", win);

    if(atop) {
        [win setLevel:NSFloatingWindowLevel];
    } else {
        [win setLevel:NSNormalWindowLevel];
    }

    DBG_PRINT( "setAlwaysOnTop0 - window: %p (END)\n", win);

    [pool release];
}

/*
 * Class:     jogamp_newt_driver_macosx_MacWindow
 * Method:    getLocationOnScreen0
 * Signature: (JII)Ljavax/media/nativewindow/util/Point;
 */
JNIEXPORT jobject JNICALL Java_jogamp_newt_driver_macosx_MacWindow_getLocationOnScreen0
  (JNIEnv *env, jclass unused, jlong win, jint src_x, jint src_y)
{
    NSObject *nsObj = (NSObject*) ((intptr_t) win);
    NewtMacWindow * mWin = NULL;

    if( [nsObj isKindOfClass:[NewtMacWindow class]] ) {
        mWin = (NewtMacWindow*) nsObj;
    } else {
        NewtCommon_throwNewRuntimeException(env, "not NewtMacWindow %p\n", nsObj);
    }

    NSPoint p0 = [mWin getLocationOnScreen: NSMakePoint(src_x, src_y)];
    return (*env)->NewObject(env, pointClz, pointCstr, (jint)p0.x, (jint)p0.y);
}

/*
 * Class:     Java_jogamp_newt_driver_macosx_MacWindow
 * Method:    setPointerVisible0
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_macosx_MacWindow_setPointerVisible0
  (JNIEnv *env, jclass clazz, jlong window, jboolean mouseVisible)
{
    NewtMacWindow *mWin = (NewtMacWindow*) ((intptr_t) window);
    [mWin setMouseVisible: ( JNI_TRUE == mouseVisible ) ? YES : NO];
    return JNI_TRUE;
}

/*
 * Class:     Java_jogamp_newt_driver_macosx_MacWindow
 * Method:    confinePointer0
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_macosx_MacWindow_confinePointer0
  (JNIEnv *env, jclass clazz, jlong window, jboolean confine)
{
    NewtMacWindow *mWin = (NewtMacWindow*) ((intptr_t) window);
    [mWin setMouseConfined: ( JNI_TRUE == confine ) ? YES : NO];
    return JNI_TRUE;
}

/*
 * Class:     Java_jogamp_newt_driver_macosx_MacWindow
 * Method:    warpPointer0
 * Signature: (JJII)V
 */
JNIEXPORT void JNICALL Java_jogamp_newt_driver_macosx_MacWindow_warpPointer0
  (JNIEnv *env, jclass clazz, jlong window, jint x, jint y)
{
    NewtMacWindow *mWin = (NewtMacWindow*) ((intptr_t) window);
    [mWin setMousePosition: [mWin newtClientWinPos2OSXScreenPos: NSMakePoint(x, y)]];
}

