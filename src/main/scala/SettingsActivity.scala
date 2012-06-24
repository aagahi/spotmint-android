package com.spotmint.android

import android.preference.{Preference, ListPreference, PreferenceActivity}
import android.os.Bundle
import android.util.Log
import android.preference.Preference.OnPreferenceChangeListener
import android.content.{Intent, Context, BroadcastReceiver, IntentFilter}

object SettingsActivity {
  final val PREFS_NAME_KEY = "SettingsActivity"

  final val PREFS_REDUCED_GPS_AFTER = "reduced_gps_after"
  final val PREFS_REDUCED_GPS_ACCURACY = "reduced_gps_accuracy"
  final val PREFS_DISCONNECT_TIMEOUT = "disconnect_timeout"

}

class SettingsActivity extends PreferenceActivity {
  import SettingsActivity._

  final val TAG = "SpotMint Settings Activity"



  lazy val receiver = new BroadcastReceiver(){
    def onReceive( context:Context, intent:Intent ){
      intent.getAction match {
        case MainActivity.KILL_MESSAGE =>
          finish()
      }
    }

  }
/*
  private val prefsChangeListener = new OnPreferenceChangeListener{
    override def onPreferenceChange(preference:Preference , newValue:Any) = {
      updatePreference( preference.getKey, newValue.toString.toInt )
      true
    }
    private def updatePreference( key:String, value:Int ){
      val serviceIntent = new Intent( SettingsActivity.this, classOf[MainService] )
      serviceIntent.setAction( key )
      serviceIntent.putExtra( MainService.WS_EXTRA, value )
      startService( serviceIntent )
    }
  }
*/
  override def onCreate(bundle: Bundle) {

    super.onCreate(bundle)
    Log.v( TAG, "Create Pref Activity" )

    getPreferenceManager.setSharedPreferencesName(PREFS_NAME_KEY )
    getPreferenceManager.setSharedPreferencesMode(Context.MODE_PRIVATE)
    addPreferencesFromResource(R.layout.preferences)
    registerReceiver( receiver, new IntentFilter( MainActivity.KILL_MESSAGE ) )

  /*
    val prefs = getPreferenceScreen()
    prefs.findPreference(PREFS_REDUCED_GPS_AFTER).asInstanceOf[ListPreference].setOnPreferenceChangeListener( prefsChangeListener )
    prefs.findPreference(PREFS_REDUCED_GPS_ACCURACY).asInstanceOf[ListPreference].setOnPreferenceChangeListener( prefsChangeListener )
    prefs.findPreference(PREFS_DISCONNECT_TIMEOUT).asInstanceOf[ListPreference].setOnPreferenceChangeListener( prefsChangeListener )
   */
  }

  // ------------------------------------------------------------
  // ------------------------------------------------------------
  override def onDestroy(){
    super.onDestroy();
    unregisterReceiver( receiver )
  }



}