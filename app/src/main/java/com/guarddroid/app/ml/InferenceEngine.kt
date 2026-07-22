package com.guarddroid.app.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads the bundled NaticusDroid TensorFlow Lite classifier from `assets/` and
 * runs permission-vector inference.
 *
 * The model input is a `[1, 86]` float tensor (binary permission flags) and the
 * output is a `[1, 1]` float tensor holding the probability that the app is
 * **malicious** (0.0 = benign, 1.0 = malicious).
 *
 * The class is thread-safe (inference is serialised) and [Closeable]. A single
 * long-lived instance should be reused across scans.
 */
class InferenceEngine private constructor(
    private val interpreter: Interpreter,
) : Closeable {

    private val lock = Any()

    /**
     * Runs the model on a pre-built feature vector.
     *
     * @param featureVector length must equal [PermissionSchema.NUM_FEATURES].
     * @return probability in `[0, 1]` that the app is malicious.
     */
    fun predictMaliciousProbability(featureVector: FloatArray): Float {
        require(featureVector.size == PermissionSchema.NUM_FEATURES) {
            "Expected ${PermissionSchema.NUM_FEATURES} features, got ${featureVector.size}"
        }
        val input = arrayOf(featureVector)
        val output = Array(1) { FloatArray(1) }
        synchronized(lock) {
            interpreter.run(input, output)
        }
        return output[0][0].coerceIn(0f, 1f)
    }

    /** Convenience: extract permissions -> vector -> probability in one call. */
    fun predictFromPermissions(permissions: Collection<String>): Float =
        predictMaliciousProbability(PermissionExtractor.toFeatureVector(permissions))

    override fun close() {
        synchronized(lock) { interpreter.close() }
    }

    companion object {
        private const val TAG = "InferenceEngine"

        /**
         * Creates an engine backed by the model in `assets/`. Throws if the
         * model cannot be loaded — callers should treat that as "scanning
         * unavailable" rather than crashing the scan pipeline.
         */
        fun create(context: Context): InferenceEngine {
            val model = loadModelBuffer(context, PermissionSchema.MODEL_ASSET)
            val options = Interpreter.Options().apply { numThreads = 2 }
            val interpreter = Interpreter(model, options)
            Log.i(TAG, "Loaded ${PermissionSchema.MODEL_ASSET} (${model.capacity()} bytes)")
            return InferenceEngine(interpreter)
        }

        /**
         * Reads the model asset into a direct [ByteBuffer]. Unlike memory-mapping
         * via `openFd`, this works whether or not the `.tflite` asset was stored
         * compressed in the APK, so it can never fail with
         * "this file can not be opened as a file descriptor; it is probably
         * compressed".
         */
        private fun loadModelBuffer(context: Context, assetName: String): ByteBuffer {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(bytes)
                    rewind()
                }
        }
    }
}
