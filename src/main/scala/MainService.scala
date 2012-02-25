package com.spotmint.android

import android.app.Service
import ws.nexus.websocket.client.{WebSocketEventHandler, Client}
import org.json.JSONObject
import android.util.Log

import Serializer.serialize
import android.content.{Context, Intent}
import android.location.{Location, LocationListener, LocationManager}
import android.os.{Bundle, Binder}
import java.io.Serializable
import java.net.URI
import util.Random


object MainService {
  
  final val TAG = "SpotMint Service"
  
  final val WS_MESSAGE = "MainService.WS_MESSAGE"
  final val LOCATION_MESSAGE = "MainService.LOCATION_MESSAGE"
  final val USER_MESSAGE = "MainService.USER_MESSAGE"
  final val CHANNEL_MESSAGE = "MainService.CHANNEL_MESSAGE"
  final val BACKGROUND_POLICY_MESSAGE = "MainService.BACKGROUND_POLICY_MESSAGE"

  final val WS_EXTRA = "extra"

  final val LOW_POWER_USAGE = 0
  final val HIGH_POWER_USAGE = 2
  
  final val DEFAULT_CHANNEL_NAME = "tap to change"

  final val PREFS_CHANNEL_NAME = "channel"

  final val PREFS_USER_NAME = "user.name"
  final val PREFS_USER_EMAIL = "user.email"
  final val PREFS_USER_STATUS = "user.status"
  final val PREFS_SESSION = "session"

  class LocalBinder( val service:MainService ) extends Binder
}

// ------------------------------------------------------------
class MainService extends Service {
  import MainService._


  var currentUser:User = _
  //def currentCoord = currentUser.coord

  lazy val sharedPreferences = getSharedPreferences( "MainService", Context.MODE_PRIVATE )
  var currentChannel = DEFAULT_CHANNEL_NAME

  var currentSession = ""
  var lastNetworkActivity = 0L
  val sessionTimeoutSec = 300
  def nexusURI = new URI("wss://nexus.ws/json-1.0/"+sessionTimeoutSec+"/"+currentSession)

  var stopping = false
  
  // ------------------------------------------------------------
  // Pref persistence
  // ------------------------------------------------------------
  private def loadUser():User = {
    User( User.SELF_USER_ID,
      sharedPreferences.getString( PREFS_USER_NAME, "Anonymous-" + (100 + Random.nextInt(899)) ),
      sharedPreferences.getString( PREFS_USER_EMAIL, "" ),
      sharedPreferences.getString( PREFS_USER_STATUS, "available" ),
      Coordinate.NO_COORDINATE, true )
  }
  private def saveUser( user:User ) {
    val editor = sharedPreferences.edit()
    editor.putString( PREFS_USER_NAME, user.name )
    editor.putString( PREFS_USER_EMAIL, user.email )
    editor.putString( PREFS_USER_STATUS, user.status )
    editor.commit()
  }
  private def saveChannel() {
    val editor = sharedPreferences.edit()
    editor.putString( PREFS_CHANNEL_NAME, currentChannel )
    editor.commit()
  }
  
  private def saveCurrentSession(){
    val editor = sharedPreferences.edit()
    editor.putString( PREFS_SESSION, currentSession )
    editor.commit()
  }
  

  // ------------------------------------------------------------
  // WS Client
  // ------------------------------------------------------------
  val client = new Client( new WebSocketEventHandler{
    override def onOpen( client:Client ) {
      if( currentSession.length() == 0 || lastNetworkActivity < System.currentTimeMillis - sessionTimeoutSec*1000 ){
        // shound send if timeout deconnection - 300sec
        client.send( PublisherUpdate( currentUser.toPublisher ) )
        client.send( SubscribChannel( currentChannel ) )
       }

      client.send( Publish( currentChannel, currentUser.coord ) )
    }
    override def onMessage( client:Client, text:String ){
      lastNetworkActivity = System.currentTimeMillis()
      val json = new JSONObject( text )
      val message = Serializer.deserialize( json )
      broadcast( WS_MESSAGE, message )
      message match {

        case Bound( session ) =>
          currentSession = session
          saveCurrentSession()

        case subscribedChannel:SubscribedChannel =>
          // TODO: Use publishTo instead
          client.send( Publish( currentChannel, currentUser.coord ) )
        case _ =>
      }
    }
   
    override def onStop( client:Client ){
      Log.i( "WS Stop", "Reconnect " + nexusURI.toString + " / stopping:" + stopping )
      if(!stopping)
        client.connect( nexusURI, Client.ConnectionOption.DEFAULT  )

    }
    override def onError( client:Client, t:Throwable ){
      Log.e( "WS Error", "Throwable", t )
    }
  })

  

  // ------------------------------------------------------------
  // Location MGMT
  // ------------------------------------------------------------
  var locationManager:LocationManager = _
  val locationListener = new LocationListener {
    override def onLocationChanged(location: Location) {
      val coord = Coordinate( location )
      currentUser = currentUser.update( coord )
      Log.v(TAG, "Client state " + client.clientThread.isAlive + " / " + client.clientThread.running.get )
      client.send( Publish( currentChannel, coord ) )
      broadcast( LOCATION_MESSAGE, coord )
    }

    override def onStatusChanged(p1: String, p2: Int, p3: Bundle) {
    }

    override def onProviderEnabled(p1: String) {}

    override def onProviderDisabled(p1: String) {}
  }

  def startLocation(){
    locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
    val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

    val coord = Coordinate( lastKnownLocation )

    currentUser = currentUser.update( coord )
    broadcast( USER_MESSAGE, currentUser )

    registerHighAccuracyLocationManager()
  }

  @inline private def registerHighAccuracyLocationManager(){
    Log.v(TAG, "High Accurracy Location"  )

    val minTimeMilisec = 1000*60
    val minDistanceMeter = 10
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMilisec, minDistanceMeter, locationListener )
    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMilisec, minDistanceMeter, locationListener )
  }

  @inline private def registerLowAccuracyLocationManager(){
    Log.v(TAG, "Low Accurracy Location"  )

    val minTimeMilisec = 1000*60*3
    val minDistanceMeter = 50
    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMilisec, minDistanceMeter, locationListener )
  }

  // ------------------------------------------------------------
  // Broadcast MGMT
  // ------------------------------------------------------------
  private def broadcast( messageType:String, message:Serializable ){
    Log.v(TAG, "Broadcast %s => %s" format ( messageType, message.toString ) )
    val broadCastIntent = new Intent( messageType )
    broadCastIntent.putExtra( WS_EXTRA, message )
    sendBroadcast( broadCastIntent )
  }


  // ------------------------------------------------------------
  // Service Lifecycle
  // ------------------------------------------------------------
  override def onStartCommand( intent:Intent, flags:Int, startId:Int ) = {
    intent.getAction match {
      case WS_MESSAGE =>
        val msg = intent.getSerializableExtra( WS_EXTRA ).asInstanceOf[WSUpMessage]
        msg match {
          case PublisherUpdate( publisher )=>
            saveUser( User( publisher ) )
            client.send( msg )

          case SubscribChannel( channel ) =>
            client.send( UnsubscribChannel( currentChannel ) )
            currentChannel = channel
            saveChannel()
            client.send( msg )
            client.send( Publish( currentChannel, currentUser.coord ) )

          case _ =>
            client.send( msg )
        }


      case BACKGROUND_POLICY_MESSAGE =>
        intent.getIntExtra( WS_EXTRA, LOW_POWER_USAGE ) match {
          case LOW_POWER_USAGE =>
            locationManager.removeUpdates( locationListener )
            registerLowAccuracyLocationManager()



          case HIGH_POWER_USAGE =>
            locationManager.removeUpdates( locationListener )
            registerHighAccuracyLocationManager()
        }


      case _ =>


    }

    Service.START_STICKY
  }



  override def onCreate(){
    super.onCreate()
    stopping = false

    currentSession = sharedPreferences.getString( PREFS_SESSION, "" )
    currentChannel = sharedPreferences.getString( PREFS_CHANNEL_NAME, DEFAULT_CHANNEL_NAME )
    currentUser = loadUser()
    startLocation()
    client.connect( nexusURI, Client.ConnectionOption.DEFAULT )

    broadcast( CHANNEL_MESSAGE, currentChannel )

  }

  
  override def onDestroy(){
    super.onDestroy()
    stopping = true
    locationManager.removeUpdates( locationListener )
    client.close()
  }


  lazy val binder = new LocalBinder( this )
  override def onBind( intent: Intent) = binder


}
