package com.guarddroid.samples

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/**
 * A deliberately trivial, BENIGN activity. Every "malware" test flavor uses
 * this same screen — the only thing that differs between flavors is the set of
 * permissions declared in the flavor manifest. These apps perform no harmful
 * action; they exist solely so GuardDroid's permission-based classifier has a
 * realistic permission profile to score.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = TextView(this).apply {
            text = buildString {
                append("GuardDroid test fixture\n\n")
                append("This is a harmless app that only *declares* a permission ")
                append("profile matching a malware archetype.\n\n")
                append("It performs NO malicious action. Install it, then open ")
                append("GuardDroid and tap “Scan Now”, or watch for the install alert.")
            }
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(56, 56, 56, 56)
        }
        setContentView(message)
    }
}
