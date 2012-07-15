package com.spotmint.android

import android.preference.{Preference, ListPreference, PreferenceActivity}
import android.os.Bundle
import android.util.{AttributeSet, Log}
import android.preference.Preference.OnPreferenceChangeListener
import android.content._
import javax.swing.event.ChangeListener


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


  class ChangeListener( listPreference:ListPreference, summaryFormat:String ) extends OnPreferenceChangeListener{

    formatSummary( listPreference.getValue )

    def formatSummary( str:String ){
      val entry = listPreference.getEntries()( listPreference.getEntryValues().indexOf( str ) )
      listPreference.setSummary( summaryFormat format entry )

    }

    override def onPreferenceChange(preference:Preference , newValue:Any) = {
      formatSummary( newValue.toString )
      true
    }
  }


  override def onCreate(bundle: Bundle) {

    super.onCreate(bundle)
    Log.v( TAG, "Create Pref Activity" )

    getPreferenceManager.setSharedPreferencesName( PREFS_NAME_KEY )
    getPreferenceManager.setSharedPreferencesMode(Context.MODE_PRIVATE)
    addPreferencesFromResource(R.layout.preferences)
    registerReceiver( receiver, new IntentFilter( MainActivity.KILL_MESSAGE ) )

    val screen = getPreferenceScreen()

    val l1 = screen.findPreference(PREFS_REDUCED_GPS_AFTER).asInstanceOf[ListPreference]
    l1.setOnPreferenceChangeListener( new ChangeListener( l1, getString( R.string.reduced_gps_after_summary ) ) )

    val l2 = screen.findPreference(PREFS_REDUCED_GPS_ACCURACY).asInstanceOf[ListPreference]
    l2.setOnPreferenceChangeListener( new ChangeListener( l2, getString( R.string.reduced_gps_accuracy_summary ) ) )

    val l3 = screen.findPreference(PREFS_DISCONNECT_TIMEOUT).asInstanceOf[ListPreference]
    l3.setOnPreferenceChangeListener( new ChangeListener( l3, getString( R.string.disconnect_timeout_summary ) ) )
  }

  // ------------------------------------------------------------
  // ------------------------------------------------------------
  override def onDestroy(){
    super.onDestroy();
    unregisterReceiver( receiver )
  }



}