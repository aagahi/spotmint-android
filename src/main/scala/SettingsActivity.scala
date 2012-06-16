package com.spotmint.android

import android.preference.PreferenceActivity
import android.os.Bundle
import android.util.Log

class SettingsActivity extends PreferenceActivity {
  final val TAG = "SpotMint Settings Activity"

  override def onCreate(bundle: Bundle) {

    super.onCreate(bundle)
    Log.v( TAG, "Create Pref Activity" )

    addPreferencesFromResource(R.layout.preferences)
  }


}