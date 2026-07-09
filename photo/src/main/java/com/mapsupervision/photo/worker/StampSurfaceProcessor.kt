package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.SurfaceOutput
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StampSurfaceProcessor(
    private val repository: com.mapsupervision.domain.repository.StampDataRepository
) : SurfaceProcessor, SurfaceTexture.OnFrameAvailableListener {

    private val glThread = HandlerThread("StampGLThread").apply { start() }
    private val glHandler = Handler(glThread.looper)
    private val glExecutor = Executor { command -> glHandler.post(command) }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // EGL objects (managed on GL thread)
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglDummySurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // Inputs/Outputs
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var inputTextureId = -1
    private val outputs = mutableMapOf<SurfaceOutput, OutputSurfaceSpec>()

    // GL program variables
    private var oesProgram = -1
    private var textureProgram = -1

    private class ViewportTextureCache(
        val width: Int,
        val height: Int,
        val bitmap: Bitmap,
        val canvas: Canvas,
        val textureId: Int
    ) {
        fun release() {
            bitmap.recycle()
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }

    private val textureCaches = mutableMapOf<Pair<Int, Int>, ViewportTextureCache>()
    private var lastRenderedSize: Pair<Int, Int>? = null

    // Stamp properties
    @Volatile
    private var currentStamp: CaptureStamp? = null
    @Volatile
    private var currentTileBitmap: Bitmap? = null
    @Volatile
    private var stampEnabled = true
    @Volatile
    private var stampBitmapPending = false

    @Volatile
    private var currentAspectRatio = CameraAspectRatio.RATIO_4_3

    fun updateAspectRatio(ratio: CameraAspectRatio) {
        currentAspectRatio = ratio
        stampBitmapPending = true
        triggerRedraw()
    }

    // Geometry buffers
    private val vertexBuffer: FloatBuffer
    private val textureCoordsBuffer: FloatBuffer

    init {
        val squareCoords = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(squareCoords)
                position(0)
            }
        }

        val textureCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        textureCoordsBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(textureCoords)
                position(0)
            }
        }

        glHandler.post { initEGL() }

        // Start listening to the repository updates
        scope.launch {
            repository.stampSnapshot.collect { snapshot ->
                if (snapshot != null) {
                    val stamp = CaptureStamp(
                        timestampMs = snapshot.timestampMs,
                        latitude = snapshot.locationKey?.let { it.latitudeE4 / 10000.0 },
                        longitude = snapshot.locationKey?.let { it.longitudeE4 / 10000.0 },
                        address = snapshot.address,
                        note = snapshot.note,
                        bearingDeg = snapshot.bearingBucket.toFloat(),
                        mapScene = snapshot.mapScene
                    )
                    currentStamp = stamp
                    stampBitmapPending = true
                    triggerRedraw()
                } else {
                    currentStamp = null
                    stampBitmapPending = true
                    triggerRedraw()
                }
            }
        }
        scope.launch {
            repository.currentTile.collect { tile ->
                currentTileBitmap = tile as? Bitmap
                stampBitmapPending = true
                triggerRedraw()
            }
        }
    }

    fun updateStamp(stamp: CaptureStamp, tileBitmap: Bitmap?, enabled: Boolean) {
        currentStamp = stamp
        currentTileBitmap = tileBitmap
        stampEnabled = enabled
        stampBitmapPending = true
        triggerRedraw()
    }

    private fun triggerRedraw() {
        glHandler.post {
            inputSurfaceTexture?.let {
                if (outputs.isNotEmpty()) {
                    renderFrame()
                }
            }
        }
    }

    // SurfaceProcessor Overrides
    override fun onInputSurface(request: SurfaceRequest) {
        glHandler.post {
            try {
                inputSurfaceTexture?.release()
                inputSurface?.release()

                inputTextureId = createOesTexture()
                val texture = SurfaceTexture(inputTextureId)
                texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                texture.setOnFrameAvailableListener(this, glHandler)

                val surface = Surface(texture)
                inputSurfaceTexture = texture
                inputSurface = surface

                request.provideSurface(surface, glExecutor) {
                    glHandler.post {
                        AppLogger.d("StampSurfaceProcessor: input surface released")
                        inputSurface?.release()
                        inputSurfaceTexture?.release()
                        inputSurface = null
                        inputSurfaceTexture = null
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(e, "StampSurfaceProcessor: onInputSurface failed")
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        glHandler.post {
            try {
                val surface = surfaceOutput.getSurface(glExecutor) {
                    glHandler.post {
                        outputs.remove(surfaceOutput)?.let { spec ->
                            EGL14.eglDestroySurface(eglDisplay, spec.eglSurface)
                            AppLogger.d("StampSurfaceProcessor: output surface removed size=${spec.width}x${spec.height}")
                        }
                    }
                }
                val attribList = intArrayOf(EGL14.EGL_NONE)
                val eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, surface, attribList, 0
                )
                if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                    throw RuntimeException("eglCreateWindowSurface failed")
                }
                outputs[surfaceOutput] = OutputSurfaceSpec(
                    eglSurface = eglSurface,
                    width = surfaceOutput.size.width,
                    height = surfaceOutput.size.height
                )
                AppLogger.d("StampSurfaceProcessor: output surface added size=${surfaceOutput.size.width}x${surfaceOutput.size.height}")
            } catch (e: Exception) {
                AppLogger.e(e, "StampSurfaceProcessor: onOutputSurface failed")
            }
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        renderFrame()
    }

    // OpenGL/EGL Methods
    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("eglGetDisplay failed")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(
            eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfigs, 0
        )
        eglConfig = configs[0] ?: throw RuntimeException("eglChooseConfig failed")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw RuntimeException("eglCreateContext failed")

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
        eglDummySurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        makeCurrent(eglDummySurface)

        setupShaders()
    }

    private fun makeCurrent(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun setupShaders() {
        val vertexShaderCode = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        val oesFragmentShaderCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()

        val simpleVertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                // Flip texture coordinate vertically because bitmap pixels start top-left but GL textures start bottom-left
                vTextureCoord = vec2(aTextureCoord.x, 1.0 - aTextureCoord.y);
            }
        """.trimIndent()

        val textureFragmentShaderCode = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()

        oesProgram = compileProgram(vertexShaderCode, oesFragmentShaderCode)
        textureProgram = compileProgram(simpleVertexShaderCode, textureFragmentShaderCode)
    }

    private fun compileProgram(vs: String, fs: String): Int {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            throw RuntimeException("Program link failed: " + GLES20.glGetProgramInfoLog(program))
        }
        return program
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            throw RuntimeException("Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
        }
        return shader
    }

    private fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createNormalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun renderFrame() {
        val texture = inputSurfaceTexture ?: return
        try {
            texture.updateTexImage()
            val texMatrix = FloatArray(16)
            texture.getTransformMatrix(texMatrix)

            outputs.forEach { (surfaceOutput, spec) ->
                makeCurrent(spec.eglSurface)

                val outW = spec.width
                val outH = spec.height
                val viewport = calculateAspectCropRect(outW, outH, currentAspectRatio)

                // Clear entire screen to black
                GLES20.glViewport(0, 0, outW, outH)
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                // Render inside target viewport aspect ratio
                GLES20.glViewport(viewport.left, viewport.top, viewport.width, viewport.height)

                val outputGLMatrix = FloatArray(16)
                surfaceOutput.updateTransformMatrix(outputGLMatrix, texMatrix)

                // Pass 1: Render Camera frame
                drawOesTexture(inputTextureId, outputGLMatrix)

                // Pass 2: Draw Stamp overlay if enabled
                if (stampEnabled) {
                    val stamp = currentStamp
                    if (stamp != null) {
                        val sizeKey = Pair(viewport.width, viewport.height)
                        val cache = textureCaches.getOrPut(sizeKey) {
                            val bitmap = Bitmap.createBitmap(viewport.width, viewport.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            val tex = createNormalTexture()
                            ViewportTextureCache(viewport.width, viewport.height, bitmap, canvas, tex)
                        }

                        if (stampBitmapPending || sizeKey != lastRenderedSize) {
                            cache.bitmap.eraseColor(Color.TRANSPARENT)
                            val tile = currentTileBitmap
                            PhotoStampRenderer.drawStamp(
                                canvas = cache.canvas,
                                frameWidth = viewport.width.toFloat(),
                                frameHeight = viewport.height.toFloat(),
                                stamp = stamp,
                                tileBitmap = tile,
                                missingLocationText = "Khong co vi tri"
                            )
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cache.textureId)
                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, cache.bitmap, 0)
                            
                            lastRenderedSize = sizeKey
                        }
                        drawStampTexture(cache.textureId)
                    }
                }

                EGL14.eglSwapBuffers(eglDisplay, spec.eglSurface)
            }
            stampBitmapPending = false
        } catch (e: Exception) {
            AppLogger.e(e, "StampSurfaceProcessor: renderFrame failed")
        }
    }

    private fun drawOesTexture(textureId: Int, texMatrix: FloatArray) {
        GLES20.glUseProgram(oesProgram)

        val uTexMatrixLoc = GLES20.glGetUniformLocation(oesProgram, "uTexMatrix")
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        val aPositionLoc = GLES20.glGetAttribLocation(oesProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        val aTextureCoordLoc = GLES20.glGetAttribLocation(oesProgram, "aTextureCoord")
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, textureCoordsBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        val sTextureLoc = GLES20.glGetUniformLocation(oesProgram, "sTexture")
        GLES20.glUniform1i(sTextureLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
    }

    private fun drawStampTexture(textureId: Int) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(textureProgram)

        val aPositionLoc = GLES20.glGetAttribLocation(textureProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        val aTextureCoordLoc = GLES20.glGetAttribLocation(textureProgram, "aTextureCoord")
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, textureCoordsBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val sTextureLoc = GLES20.glGetUniformLocation(textureProgram, "sTexture")
        GLES20.glUniform1i(sTextureLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        scope.cancel()
        glHandler.post {
            try {
                inputSurface?.release()
                inputSurfaceTexture?.release()
                inputSurface = null
                inputSurfaceTexture = null

                outputs.forEach { (_, spec) ->
                    EGL14.eglDestroySurface(eglDisplay, spec.eglSurface)
                }
                outputs.clear()

                textureCaches.forEach { (_, cache) ->
                    cache.release()
                }
                textureCaches.clear()

                GLES20.glDeleteProgram(oesProgram)
                GLES20.glDeleteProgram(textureProgram)
                GLES20.glDeleteTextures(1, intArrayOf(inputTextureId), 0)

                EGL14.eglDestroySurface(eglDisplay, eglDummySurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)

                eglDisplay = EGL14.EGL_NO_DISPLAY
                eglContext = EGL14.EGL_NO_CONTEXT
                eglDummySurface = EGL14.EGL_NO_SURFACE
            } catch (e: Exception) {
                AppLogger.e(e, "StampSurfaceProcessor: release failed")
            } finally {
                glThread.quitSafely()
            }
        }
    }

    private data class OutputSurfaceSpec(
        val eglSurface: EGLSurface,
        val width: Int,
        val height: Int
    )
}
