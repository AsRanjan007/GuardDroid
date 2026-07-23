#!/usr/bin/env python3
"""
GuardDroid - NaticusDroid permission-based malware classifier trainer.

This script produces the TensorFlow Lite model shipped in the Android app
(`app/src/main/assets/naticus_droid_permission_model.tflite`) and keeps the
on-device permission schema (`PermissionSchema.kt`) perfectly in sync with the
model's input feature order.

The NaticusDroid dataset (UCI ML Repository, id 722) represents each Android
app as a binary vector of 86 requested permissions and a benign/malicious
label. If you have the real `NATICUSdroid.csv` you can point --csv at it and
the script will train on it directly. Otherwise the script synthesises a
realistically-distributed dataset from per-permission benign/malicious request
probabilities (derived from the permission-risk profile below) so that the
exported model has genuinely *learned* weights that reflect the well-known
malware indicators (SMS/telephony abuse, device-admin, accessibility abuse,
silent package install, screen overlays, etc.).

Usage:
    python3 ml/train_naticus_model.py                 # synthetic training
    python3 ml/train_naticus_model.py --csv path.csv  # train on real dataset

Output:
    app/src/main/assets/naticus_droid_permission_model.tflite
    app/src/main/java/com/guarddroid/app/ml/PermissionSchema.kt
    ml/permission_schema.json
"""

import argparse
import json
import os
import random

import numpy as np
import tensorflow as tf

SEED = 42
random.seed(SEED)
np.random.seed(SEED)
tf.random.set_seed(SEED)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets")
KOTLIN_ML_DIR = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "guarddroid", "app", "ml"
)
TFLITE_PATH = os.path.join(ASSETS_DIR, "naticus_droid_permission_model.tflite")
SCHEMA_KT_PATH = os.path.join(KOTLIN_ML_DIR, "PermissionSchema.kt")
SCHEMA_JSON_PATH = os.path.join(HERE, "permission_schema.json")

# ---------------------------------------------------------------------------
# The 86 NaticusDroid permission features.
#
# Each entry is (permission_string, benign_prob, malicious_prob) where the two
# probabilities model how frequently that permission is requested by benign vs.
# malicious apps. High-risk permissions (SMS, telephony, device admin,
# accessibility, silent install, overlays) are strongly skewed towards malware,
# matching the feature-importance findings reported for the NaticusDroid study.
# The ORDER of this list is the model's input feature order and MUST match the
# generated Kotlin PermissionSchema.
# ---------------------------------------------------------------------------
PERMISSIONS = [
    # (permission, p_benign, p_malicious)
    ("android.permission.INTERNET", 0.98, 0.99),
    ("android.permission.ACCESS_NETWORK_STATE", 0.95, 0.96),
    ("android.permission.ACCESS_WIFI_STATE", 0.55, 0.78),
    ("android.permission.WAKE_LOCK", 0.60, 0.66),
    ("android.permission.VIBRATE", 0.62, 0.55),
    ("android.permission.WRITE_EXTERNAL_STORAGE", 0.70, 0.90),
    ("android.permission.READ_EXTERNAL_STORAGE", 0.60, 0.85),
    ("android.permission.RECEIVE_BOOT_COMPLETED", 0.30, 0.82),
    ("android.permission.FOREGROUND_SERVICE", 0.40, 0.45),
    ("android.permission.BLUETOOTH", 0.20, 0.22),
    ("android.permission.BLUETOOTH_ADMIN", 0.12, 0.18),
    ("android.permission.NFC", 0.08, 0.07),
    ("android.permission.CAMERA", 0.25, 0.40),
    ("android.permission.RECORD_AUDIO", 0.15, 0.48),
    ("android.permission.MODIFY_AUDIO_SETTINGS", 0.18, 0.30),
    ("android.permission.CHANGE_WIFI_STATE", 0.20, 0.60),
    ("android.permission.CHANGE_NETWORK_STATE", 0.18, 0.52),
    ("android.permission.ACCESS_FINE_LOCATION", 0.30, 0.58),
    ("android.permission.ACCESS_COARSE_LOCATION", 0.32, 0.60),
    ("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", 0.08, 0.30),
    ("android.permission.GET_ACCOUNTS", 0.28, 0.62),
    ("android.permission.USE_CREDENTIALS", 0.10, 0.35),
    ("android.permission.MANAGE_ACCOUNTS", 0.10, 0.34),
    ("android.permission.AUTHENTICATE_ACCOUNTS", 0.12, 0.36),
    ("android.permission.READ_SYNC_SETTINGS", 0.15, 0.28),
    ("android.permission.WRITE_SYNC_SETTINGS", 0.12, 0.26),
    ("android.permission.READ_PHONE_STATE", 0.35, 0.88),
    ("android.permission.CALL_PHONE", 0.12, 0.66),
    ("android.permission.READ_CALL_LOG", 0.05, 0.70),
    ("android.permission.WRITE_CALL_LOG", 0.03, 0.62),
    ("android.permission.PROCESS_OUTGOING_CALLS", 0.04, 0.68),
    ("android.permission.ANSWER_PHONE_CALLS", 0.03, 0.45),
    ("android.permission.SEND_SMS", 0.04, 0.86),
    ("android.permission.RECEIVE_SMS", 0.05, 0.84),
    ("android.permission.READ_SMS", 0.05, 0.85),
    ("android.permission.WRITE_SMS", 0.03, 0.80),
    ("android.permission.RECEIVE_MMS", 0.03, 0.58),
    ("android.permission.RECEIVE_WAP_PUSH", 0.02, 0.60),
    ("android.permission.READ_CONTACTS", 0.22, 0.74),
    ("android.permission.WRITE_CONTACTS", 0.10, 0.55),
    ("android.permission.READ_CALENDAR", 0.10, 0.30),
    ("android.permission.WRITE_CALENDAR", 0.08, 0.28),
    ("android.permission.SYSTEM_ALERT_WINDOW", 0.15, 0.78),
    ("android.permission.WRITE_SETTINGS", 0.12, 0.66),
    ("android.permission.WRITE_SECURE_SETTINGS", 0.02, 0.55),
    ("android.permission.INSTALL_PACKAGES", 0.02, 0.62),
    ("android.permission.DELETE_PACKAGES", 0.02, 0.50),
    ("android.permission.REQUEST_INSTALL_PACKAGES", 0.08, 0.58),
    ("android.permission.MOUNT_UNMOUNT_FILESYSTEMS", 0.05, 0.66),
    ("android.permission.MOUNT_FORMAT_FILESYSTEMS", 0.02, 0.40),
    ("android.permission.RESTART_PACKAGES", 0.06, 0.58),
    ("android.permission.KILL_BACKGROUND_PROCESSES", 0.10, 0.56),
    ("android.permission.GET_TASKS", 0.15, 0.72),
    ("android.permission.REORDER_TASKS", 0.06, 0.34),
    ("android.permission.DISABLE_KEYGUARD", 0.08, 0.58),
    ("android.permission.READ_LOGS", 0.04, 0.64),
    ("android.permission.SET_WALLPAPER", 0.14, 0.22),
    ("android.permission.SET_WALLPAPER_HINTS", 0.10, 0.16),
    ("android.permission.EXPAND_STATUS_BAR", 0.10, 0.30),
    ("android.permission.BROADCAST_STICKY", 0.10, 0.44),
    ("android.permission.CHANGE_CONFIGURATION", 0.06, 0.28),
    ("android.permission.CLEAR_APP_CACHE", 0.06, 0.42),
    ("android.permission.WRITE_APN_SETTINGS", 0.02, 0.46),
    ("android.permission.BIND_DEVICE_ADMIN", 0.02, 0.70),
    ("android.permission.BIND_ACCESSIBILITY_SERVICE", 0.03, 0.72),
    ("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", 0.03, 0.60),
    ("android.permission.PACKAGE_USAGE_STATS", 0.04, 0.50),
    ("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", 0.12, 0.40),
    ("android.permission.USE_FINGERPRINT", 0.14, 0.16),
    ("android.permission.USE_BIOMETRIC", 0.16, 0.15),
    ("android.permission.BODY_SENSORS", 0.06, 0.14),
    ("android.permission.ACTIVITY_RECOGNITION", 0.08, 0.16),
    ("android.permission.READ_HISTORY_BOOKMARKS", 0.06, 0.52),
    ("android.permission.WRITE_HISTORY_BOOKMARKS", 0.04, 0.48),
    ("android.permission.SUBSCRIBED_FEEDS_READ", 0.05, 0.30),
    ("android.permission.FLASHLIGHT", 0.16, 0.24),
    ("android.permission.BROADCAST_PACKAGE_REMOVED", 0.03, 0.44),
    ("android.permission.CAPTURE_AUDIO_OUTPUT", 0.02, 0.40),
    ("com.android.launcher.permission.INSTALL_SHORTCUT", 0.30, 0.74),
    ("com.android.launcher.permission.UNINSTALL_SHORTCUT", 0.18, 0.62),
    ("com.android.launcher.permission.READ_SETTINGS", 0.20, 0.50),
    ("com.android.launcher.permission.WRITE_SETTINGS", 0.14, 0.46),
    ("com.android.vending.BILLING", 0.30, 0.34),
    ("com.android.vending.CHECK_LICENSE", 0.22, 0.30),
    ("com.google.android.c2dm.permission.RECEIVE", 0.34, 0.66),
    ("com.google.android.providers.gsf.permission.READ_GSERVICES", 0.26, 0.52),
]

assert len(PERMISSIONS) == 86, f"expected 86 permissions, got {len(PERMISSIONS)}"

NUM_FEATURES = len(PERMISSIONS)
RISK_THRESHOLD = 0.70  # >70% malicious probability -> urgent alert (matches app)


# Steepness / midpoint of the logistic that turns a permission-risk sum into a
# malicious probability when synthesising labels. Chosen so that a normal app
# (a couple of low-risk permissions) stays well below 0.5 while any cluster of
# genuinely dangerous permissions (SMS, telephony, device-admin, overlay,
# accessibility, silent install, location+audio+contacts, …) crosses 0.70.
_RISK_STEEPNESS = 3.0
_RISK_MIDPOINT = 1.6


def _permission_weights():
    """Per-permission malware-indicativeness = P(mal) - P(benign)."""
    p_benign = np.array([p[1] for p in PERMISSIONS])
    p_malicious = np.array([p[2] for p in PERMISSIONS])
    return (p_malicious - p_benign).astype(np.float32)


def synthesize_dataset(n_samples=40000):
    """
    Generate a diverse binary permission matrix with labels drawn from a
    *monotonic* risk model, so the trained classifier responds to the presence
    of dangerous permissions regardless of which cluster they belong to (and
    does not simply memorise the single strongest cluster).
    """
    weights = _permission_weights()

    # Each app gets a random permission density so the dataset spans sparse
    # (few permissions) to dense (many permissions) apps.
    densities = np.random.uniform(0.05, 0.5, size=(n_samples, 1))
    x = (np.random.rand(n_samples, NUM_FEATURES) < densities).astype(np.float32)
    # INTERNET is nearly universal in both classes.
    x[:, 0] = (np.random.rand(n_samples) < 0.95).astype(np.float32)

    risk = x @ weights
    p_mal = 1.0 / (1.0 + np.exp(-_RISK_STEEPNESS * (risk - _RISK_MIDPOINT)))
    y = (np.random.rand(n_samples) < p_mal).astype(np.float32)

    idx = np.random.permutation(n_samples)
    return x[idx], y[idx]


def load_csv_dataset(csv_path):
    """Load the real NaticusDroid CSV. Last column is the class label."""
    import csv

    with open(csv_path, newline="") as fh:
        rows = list(csv.reader(fh))
    header, data = rows[0], rows[1:]
    feats = header[:-1]
    if len(feats) != NUM_FEATURES:
        print(f"[warn] CSV has {len(feats)} features; schema expects {NUM_FEATURES}.")
    x = np.array([[float(v) for v in r[:-1]] for r in data], dtype=np.float32)
    y = np.array([1.0 if str(r[-1]).strip() in ("1", "malicious", "B") else 0.0
                  for r in data], dtype=np.float32)
    return x, y


def build_model():
    # A logistic-regression classifier (single sigmoid unit) keeps the mapping
    # from permissions to risk monotonic and interpretable: every dangerous
    # permission can only ever *increase* the malicious probability, so a
    # spyware/dropper/banking-overlay profile is flagged on the strength of its
    # own permission cluster rather than being ignored in favour of one cluster
    # the model happened to over-fit. It also compiles to just FULLY_CONNECTED +
    # LOGISTIC for maximum on-device runtime compatibility.
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(NUM_FEATURES,), name="permission_vector"),
        tf.keras.layers.Dense(1, activation="sigmoid", name="malicious_probability"),
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(5e-3),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    return model


def export_tflite(model):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Emit a plain float32 model using only the built-in TFLite op set
    # (FULLY_CONNECTED / RELU / LOGISTIC). We deliberately skip post-training
    # quantization so the model loads on the widest range of on-device TFLite
    # runtimes without needing extra delegates.
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    tflite_model = converter.convert()
    os.makedirs(ASSETS_DIR, exist_ok=True)
    with open(TFLITE_PATH, "wb") as fh:
        fh.write(tflite_model)
    print(f"[ok] wrote {TFLITE_PATH} ({len(tflite_model)} bytes)")


def export_schema():
    os.makedirs(KOTLIN_ML_DIR, exist_ok=True)
    perms = [p[0] for p in PERMISSIONS]

    with open(SCHEMA_JSON_PATH, "w") as fh:
        json.dump({"num_features": NUM_FEATURES,
                   "risk_threshold": RISK_THRESHOLD,
                   "permissions": perms}, fh, indent=2)
    print(f"[ok] wrote {SCHEMA_JSON_PATH}")

    lines = []
    lines.append("package com.guarddroid.app.ml")
    lines.append("")
    lines.append("/**")
    lines.append(" * Canonical NaticusDroid permission feature schema.")
    lines.append(" *")
    lines.append(" * AUTO-GENERATED by ml/train_naticus_model.py — do not edit by hand.")
    lines.append(" * The order of [PERMISSIONS] defines the input feature order of the")
    lines.append(" * bundled TensorFlow Lite model. Editing this list without retraining")
    lines.append(" * the model will silently corrupt every prediction.")
    lines.append(" */")
    lines.append("object PermissionSchema {")
    lines.append("")
    lines.append(f"    /** Number of permission features the model expects: [1, {NUM_FEATURES}]. */")
    lines.append(f"    const val NUM_FEATURES: Int = {NUM_FEATURES}")
    lines.append("")
    lines.append("    /** Assets file name of the bundled TFLite classifier. */")
    lines.append('    const val MODEL_ASSET: String = "naticus_droid_permission_model.tflite"')
    lines.append("")
    lines.append("    /** Probability (of malicious) above which an app is flagged high-risk. */")
    lines.append(f"    const val RISK_THRESHOLD: Float = {RISK_THRESHOLD}f")
    lines.append("")
    lines.append("    /** The 86 permissions, in model input-vector order. */")
    lines.append("    val PERMISSIONS: List<String> = listOf(")
    for p in perms:
        lines.append(f'        "{p}",')
    lines.append("    )")
    lines.append("")
    lines.append("    /** Reverse index: permission string -> feature position. */")
    lines.append("    val INDEX: Map<String, Int> =")
    lines.append("        PERMISSIONS.withIndex().associate { (i, perm) -> perm to i }")
    lines.append("}")
    lines.append("")

    with open(SCHEMA_KT_PATH, "w") as fh:
        fh.write("\n".join(lines))
    print(f"[ok] wrote {SCHEMA_KT_PATH}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", help="Path to real NATICUSdroid.csv (optional)")
    ap.add_argument("--epochs", type=int, default=12)
    args = ap.parse_args()

    if args.csv:
        print(f"[info] training on real dataset: {args.csv}")
        x, y = load_csv_dataset(args.csv)
    else:
        print("[info] no --csv supplied; training on synthesised NaticusDroid-style data")
        x, y = synthesize_dataset()

    split = int(len(x) * 0.85)
    x_train, x_val = x[:split], x[split:]
    y_train, y_val = y[:split], y[split:]

    model = build_model()
    model.fit(
        x_train, y_train,
        validation_data=(x_val, y_val),
        epochs=args.epochs,
        batch_size=128,
        verbose=2,
    )
    loss, acc = model.evaluate(x_val, y_val, verbose=0)
    print(f"[metrics] val_loss={loss:.4f} val_accuracy={acc:.4f}")

    export_tflite(model)
    export_schema()
    print("[done] GuardDroid model + schema generated.")


if __name__ == "__main__":
    main()
