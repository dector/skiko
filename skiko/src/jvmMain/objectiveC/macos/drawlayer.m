#import "jawt.h"
#import "jawt_md.h"

#define GL_SILENCE_DEPRECATION

#define kNullWindowHandle NULL

#import <Cocoa/Cocoa.h>
#import <QuartzCore/QuartzCore.h>
#import <OpenGL/gl3.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

#import "GrContext.h"

JavaVM *jvm = NULL;

@interface AWTGLLayer : CAOpenGLLayer

@property jobject windowRef;

@end

@implementation AWTGLLayer

jobject windowRef;

- (id)init
{
    self = [super init];

    if (self)
    {
        [self removeAllAnimations];
        [self setAutoresizingMask: (kCALayerWidthSizable|kCALayerHeightSizable)];
        [self setNeedsDisplayOnBoundsChange: YES];

        self.windowRef = NULL;
    }

    return self;
}

-(void)drawInCGLContext:(CGLContextObj)ctx 
            pixelFormat:(CGLPixelFormatObj)pf 
            forLayerTime:(CFTimeInterval)t 
            displayTime:(const CVTimeStamp *)ts
{
    CGLSetCurrentContext(ctx);

    if (jvm != NULL) {
        JNIEnv *env;
        (*jvm)->AttachCurrentThread(jvm, (void **)&env, NULL);

        static jclass wndClass = NULL;
        if (!wndClass) wndClass = (*env)->GetObjectClass(env, self.windowRef);
        static jmethodID drawMethod = NULL;
        if (!drawMethod) drawMethod = (*env)->GetMethodID(env, wndClass, "draw", "()V");
        if (NULL == drawMethod) {
            NSLog(@"The method Window.draw() not found!");
            return;
        }
        (*env)->CallVoidMethod(env, self.windowRef, drawMethod);
    }

    [super drawInCGLContext:ctx pixelFormat:pf forLayerTime:t displayTime:ts];
}

- (void) dispose
{
    self.windowRef = NULL;
}

@end

@interface LayersSet : NSObject

@property jobject windowRef;
@property (retain, strong) CALayer *caLayer;
@property (retain, strong) AWTGLLayer *glLayer;
@property (retain, strong) NSWindow *window;

@end

@implementation LayersSet

jobject windowRef;
CALayer *caLayer;
AWTGLLayer *glLayer;

- (id) init
{
    self = [super init];

    if (self)
    {
        self.windowRef = NULL;
        self.caLayer = NULL;
        self.glLayer = NULL;
        self.window = NULL;
    }

    return self;
}

- (void) syncSize
{
    float scaleFactor = [[self.window screen] backingScaleFactor];
    self.caLayer.contentsScale = scaleFactor;
    self.glLayer.contentsScale = scaleFactor;
    self.glLayer.bounds = self.caLayer.bounds;
    self.glLayer.frame = self.caLayer.frame;
}

- (void) update
{
    [self.glLayer performSelectorOnMainThread:@selector(setNeedsDisplay) withObject:0 waitUntilDone:NO];
}

- (void) dispose
{
    [self.glLayer dispose];
    self.glLayer = NULL;
    self.caLayer = NULL;
    self.window = NULL;
}

@end

NSMutableArray *unknownWindows = nil;
NSMutableSet *windowsSet = nil;
LayersSet * findByObject(JNIEnv *env, jobject object) {
    for (LayersSet* value in windowsSet) {
        if ((*env)->IsSameObject(env, object, value.windowRef) == JNI_TRUE) {
            return value;
        }
    }

    return NULL;
}

extern jboolean Skiko_GetAWT(JNIEnv* env, JAWT* awt);

JNIEXPORT void JNICALL Java_org_jetbrains_skiko_HardwareLayer_updateLayer(JNIEnv *env, jobject window)
{
    if (windowsSet != nil) {
        LayersSet *layer = findByObject(env, window);
        if (layer != NULL) {
            if (layer.caLayer == NULL && layer.glLayer == NULL) {
                (*env)->DeleteGlobalRef(env, layer.windowRef);
                layer.windowRef = NULL;
                [windowsSet removeObject: layer];
                return;
            }
            [layer syncSize];
            return;
        }
    } else {
        windowsSet = [[NSMutableSet alloc] init];
    }

    JAWT awt;
    JAWT_DrawingSurface *ds = NULL;
    JAWT_DrawingSurfaceInfo *dsi = NULL;

    jboolean result = JNI_FALSE;
    jint lock = 0;
    NSObject<JAWT_SurfaceLayers>* dsi_mac = NULL;

    awt.version = JAWT_VERSION_9 /* | JAWT_MACOSX_USE_CALAYER */;
    result = Skiko_GetAWT(env, &awt);
    assert(result != JNI_FALSE);

    (*env)->GetJavaVM(env, &jvm);

    ds = awt.GetDrawingSurface(env, window);
    assert(ds != NULL);

    lock = ds->Lock(ds);
    assert((lock & JAWT_LOCK_ERROR) == 0);

    dsi = ds->GetDrawingSurfaceInfo(ds);

    if (dsi != NULL)
    {
        dsi_mac = ( __bridge NSObject<JAWT_SurfaceLayers> *) dsi->platformInfo;

        LayersSet *layersSet = [[LayersSet alloc] init];
        [windowsSet addObject: layersSet];

        NSMutableArray<NSWindow *> *windows = [NSMutableArray arrayWithArray: [[NSApplication sharedApplication] windows]];
        if ([windowsSet count] == 1)
        {
            NSWindow *mainWindow = [[NSApplication sharedApplication] mainWindow];
            layersSet.window = mainWindow;
            [windows removeObject: mainWindow];
            unknownWindows = windows;
        }
        else
        {
            for (NSWindow* value in unknownWindows) {
                [windows removeObject: value];
            }
            for (LayersSet* value in windowsSet) {
                [windows removeObject: value.window];
            }
            layersSet.window = [windows firstObject];
        }

        layersSet.caLayer = [dsi_mac windowLayer];
        [layersSet.caLayer removeAllAnimations];
        [layersSet.caLayer setAutoresizingMask: (kCALayerWidthSizable|kCALayerHeightSizable)];
        [layersSet.caLayer setNeedsDisplayOnBoundsChange: YES];

        layersSet.glLayer = [AWTGLLayer new];
        [layersSet.caLayer addSublayer: layersSet.glLayer];
        CGFloat white[] = { 1.0f, 1.0f, 1.0f, 1.0f };
        layersSet.glLayer.backgroundColor = CGColorCreate(CGColorSpaceCreateDeviceRGB(), white);
        
        jobject windowRef = (*env)->NewGlobalRef(env, window);

        [layersSet.glLayer setWindowRef: windowRef];
        [layersSet setWindowRef: windowRef];
        [layersSet syncSize];
    }

    ds->FreeDrawingSurfaceInfo(dsi);
    ds->Unlock(ds);
    awt.FreeDrawingSurface(ds);
}

JNIEXPORT void JNICALL Java_org_jetbrains_skiko_HardwareLayer_redrawLayer(JNIEnv *env, jobject window) {
    LayersSet *layer = findByObject(env, window);
    if (layer != NULL) {
        [layer update];
    }
}

JNIEXPORT void JNICALL Java_org_jetbrains_skiko_HardwareLayer_disposeLayer(JNIEnv *env, jobject window) {
    LayersSet *layer = findByObject(env, window);
    if (layer != NULL) {
        [layer dispose];
    }
}

JNIEXPORT jfloat JNICALL Java_org_jetbrains_skiko_HardwareLayer_getContentScale(JNIEnv *env, jobject window) {
    LayersSet *layer = findByObject(env, window);
    if (layer != NULL) {
        return layer.caLayer.contentsScale;
    }
    return 1.0f;
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_skiko_HardwareLayer_getWindowHandle(JNIEnv *env, jobject window) {
    return (jlong)kNullWindowHandle;
}



////////METAL LAYER
@interface AWTMtlLayer : CAMetalLayer

@property jobject windowHandler;
@property (nonatomic, strong) id<MTLDevice> mtlDevice;
@property (nonatomic, strong) id<MTLCommandQueue> mtlCommandQueue;
@property (nonatomic, strong) MTLRenderPassDescriptor *mtlRenderDescr;

@end

@implementation AWTMtlLayer

jobject windowHandler;

- (id)init
{
    self = [super init];

    if (self)
    {
        [self removeAllAnimations];
        [self setAutoresizingMask: (kCALayerWidthSizable|kCALayerHeightSizable)];
        [self setNeedsDisplayOnBoundsChange: YES];

        self.windowHandler = NULL;
        self.mtlDevice = MTLCreateSystemDefaultDevice();
            NSLog(@"%@",self.mtlDevice.name);

        self.device = self.mtlDevice;
        if (self.device == nil) {
            NSLog(@"device is nil");
        }
        self.pixelFormat = MTLPixelFormatBGRA8Unorm;
        self.mtlCommandQueue = [self.device newCommandQueue];
        self.colorspace = CGColorSpaceCreateDeviceRGB();
        self.mtlRenderDescr = [MTLRenderPassDescriptor new];
        self.mtlRenderDescr.colorAttachments[0].loadAction = MTLLoadActionClear;
        self.mtlRenderDescr.colorAttachments[0].storeAction = MTLStoreActionStore;
        self.mtlRenderDescr.colorAttachments[0].clearColor = MTLClearColorMake(0, 1, 0, 1);
        id<MTLLibrary> shaderLib = [self.device newDefaultLibrary];
    }

    return self;
}

-(void)drawInContext:(CGContextRef)ctx {

    if (jvm != NULL)
    {
        JNIEnv *env;
        (*jvm)->AttachCurrentThread(jvm, (void **)&env, NULL);

        jclass wndClass = (*env)->GetObjectClass(env, self.windowHandler);
        static jmethodID drawMethod = NULL;
        if (!drawMethod) drawMethod = (*env)->GetMethodID(env, wndClass, "draw", "()V");
        if (NULL == drawMethod)
        {
            NSLog(@"The method Window.draw() not found!");
            return;
        }
        (*env)->CallVoidMethod(env, self.windowHandler, drawMethod);
    }

    // id<MTLCommandBuffer> commandBuffer = [self.mtlCommandQueue commandBuffer];
    // id<CAMetalDrawable> currentDrawable = [self nextDrawable];
    // if(!currentDrawable)
    // {
    //     return;
    // }
    // self.mtlRenderDescr.colorAttachments[0].texture = currentDrawable.texture;

    // id <MTLRenderCommandEncoder> renderEncoder = [commandBuffer renderCommandEncoderWithDescriptor:self.mtlRenderDescr];
    // [renderEncoder endEncoding];
    
    // [commandBuffer presentDrawable:currentDrawable];
    // [commandBuffer commit];
}

- (void) dispose
{
    self.windowHandler = NULL;
}

@end

@interface LayersMtlSet : NSObject

@property jobject windowRef;
@property (retain, strong) CALayer *container;
@property (retain, strong) AWTMtlLayer *metal;
@property (retain, strong) NSWindow *window;

@end

@implementation LayersMtlSet

CALayer *container;
AWTMtlLayer *metal;

- (id) init
{
    self = [super init];

    if (self)
    {
        self.windowRef = NULL;
        self.container = NULL;
        self.metal = NULL;
        self.window = NULL;
    }

    return self;
}

- (void) syncSize
{
    float scaleFactor = [[self.window screen] backingScaleFactor];
    self.container.contentsScale = scaleFactor;
    self.metal.contentsScale = scaleFactor;
    self.metal.bounds = self.container.bounds;
    self.metal.frame = self.container.frame;
    self.metal.drawableSize = self.container.bounds.size;
}

- (void) update
{
    [self.container performSelectorOnMainThread:@selector(setNeedsDisplay) withObject:0 waitUntilDone:NO];
    [self.metal performSelectorOnMainThread:@selector(setNeedsDisplay) withObject:0 waitUntilDone:NO];
    // [self.metal performSelectorOnMainThread:@selector(redraw) withObject:0 waitUntilDone:NO];
}

- (void) dispose
{
    [self.metal dispose];
    self.metal = NULL;
    self.container = NULL;
    self.window = NULL;
}

@end

NSMutableArray *unknownMtlWindows = nil;
NSMutableSet *windowsMtlSet = nil;
LayersMtlSet * findMtlByObject(JNIEnv *env, jobject object)
{
    for (LayersMtlSet* value in windowsMtlSet)
    {
        if ((*env)->IsSameObject(env, object, value.windowRef) == JNI_TRUE)
        {
            return value;
        }
    }

    return NULL;
}

JNIEXPORT void JNICALL Java_org_jetbrains_skiko_MetalLayer_updateLayer(JNIEnv *env, jobject window)
{
    if (windowsMtlSet != nil)
    {
        LayersMtlSet *layer = findMtlByObject(env, window);
        if (layer != NULL)
        {
            if (layer.container == NULL && layer.metal == NULL)
            {
                (*env)->DeleteGlobalRef(env, layer.windowRef);
                layer.windowRef = NULL;
                [windowsMtlSet removeObject: layer];
                return;
            }
            [layer syncSize];
            return;
        }
    }
    else
    {
        windowsMtlSet = [[NSMutableSet alloc] init];
    }

    JAWT awt;
    JAWT_DrawingSurface *ds = NULL;
    JAWT_DrawingSurfaceInfo *dsi = NULL;

    jboolean result = JNI_FALSE;
    jint lock = 0;
    NSObject<JAWT_SurfaceLayers>* dsi_mac = NULL;

    awt.version = JAWT_VERSION_9 /* | JAWT_MACOSX_USE_CALAYER */;
    result = Skiko_GetAWT(env, &awt);
    assert(result != JNI_FALSE);

    (*env)->GetJavaVM(env, &jvm);

    ds = awt.GetDrawingSurface(env, window);
    assert(ds != NULL);

    lock = ds->Lock(ds);
    assert((lock & JAWT_LOCK_ERROR) == 0);

    dsi = ds->GetDrawingSurfaceInfo(ds);

    if (dsi != NULL)
    {
        dsi_mac = ( __bridge NSObject<JAWT_SurfaceLayers> *) dsi->platformInfo;

        LayersMtlSet *layersSet = [[LayersMtlSet alloc] init];
        [windowsMtlSet addObject: layersSet];

        NSMutableArray<NSWindow *> *windows = [NSMutableArray arrayWithArray: [[NSApplication sharedApplication] windows]];
        if ([windowsMtlSet count] == 1)
        {
            NSWindow *mainWindow = [[NSApplication sharedApplication] mainWindow];
            layersSet.window = mainWindow;
            [windows removeObject: mainWindow];
            unknownMtlWindows = windows;
        }
        else
        {
            for (NSWindow* value in unknownMtlWindows)
            {
                [windows removeObject: value];
            }
            for (LayersMtlSet* value in windowsMtlSet)
            {
                [windows removeObject: value.window];
            }
            layersSet.window = [windows firstObject];
        }

        layersSet.container = [dsi_mac windowLayer];

        [layersSet.container removeAllAnimations];
        [layersSet.container setAutoresizingMask: (kCALayerWidthSizable|kCALayerHeightSizable)];
        [layersSet.container setNeedsDisplayOnBoundsChange: YES];

        layersSet.metal = [AWTMtlLayer new];

        // CGFloat white[] = { 1.0f, 0.0f, 0.0f, 1.0f };
        // layersSet.metal.backgroundColor = CGColorCreate(CGColorSpaceCreateDeviceRGB(), white);
        [layersSet.container addSublayer: layersSet.metal];
        
        jobject windowRef = (*env)->NewGlobalRef(env, window);

        [layersSet.metal setWindowHandler: windowRef];
        [layersSet setWindowRef: windowRef];
        [layersSet syncSize];
    }

    ds->FreeDrawingSurfaceInfo(dsi);
    ds->Unlock(ds);
    awt.FreeDrawingSurface(ds);
}

JNIEXPORT void JNICALL Java_org_jetbrains_skiko_MetalLayer_redrawLayer(JNIEnv *env, jobject window)
{
    LayersMtlSet *layer = findMtlByObject(env, window);
    if (layer != NULL)
    {
        [layer update];
    }
}

JNIEXPORT void JNICALL Java_org_jetbrains_skiko_MetalLayer_disposeLayer(JNIEnv *env, jobject window)
{
    LayersMtlSet *layer = findMtlByObject(env, window);
    if (layer != NULL)
    {
        [layer dispose];
    }
}

JNIEXPORT jfloat JNICALL Java_org_jetbrains_skiko_MetalLayer_getContentScale(JNIEnv *env, jobject window)
{
    LayersMtlSet *layer = findMtlByObject(env, window);
    if (layer != NULL)
    {
        return layer.container.contentsScale;
    }
    return 1.0f;
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_skiko_MetalLayer_getWindowHandle(JNIEnv *env, jobject window)
{
    return (jlong)kNullWindowHandle;
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_skiko_MetalLayer_makeMetalContext(JNIEnv *env, jobject window)
{
    LayersMtlSet *layer = findMtlByObject(env, window);
    if (layer != NULL)
    {
        NSLog(@"sk_sp<GrDirectContext> MetalLayerHandler::makeMetal()");

        id<MTLDevice> fDevice = MTLCreateSystemDefaultDevice();
        layer.metal.device = fDevice;
        layer.metal.layoutManager = [CAConstraintLayoutManager layoutManager];
        layer.metal.autoresizingMask = kCALayerHeightSizable | kCALayerWidthSizable;
        layer.metal.contentsGravity = kCAGravityTopLeft;
        layer.metal.magnificationFilter = kCAFilterNearest;
        layer.metal.pixelFormat = MTLPixelFormatBGRA8Unorm;
        layer.metal.colorspace = CGColorSpaceCreateDeviceRGB();
        layer.metal.framebufferOnly = YES;
        id<MTLCommandQueue> fQueue = [fDevice newCommandQueue];
        sk_sp<GrContext> fContext = GrContext::MakeMetal((__bridge void*)fDevice, (__bridge void*)fQueue);
    }
}

// METAL CONTEXT
JNIEXPORT jlong JNICALL Java_org_jetbrains_skiko_MetalLayer_getNativeSurface(JNIEnv* env, jobject window) {
    // NSLog(@"Java_org_jetbrains_skiko_MetalLayer_getNativeSurface");
    LayersMtlSet *layer = findMtlByObject(env, window);
    if (layer != NULL)
    {
        return (jlong)layer.metal;
    }
    return -1;
}