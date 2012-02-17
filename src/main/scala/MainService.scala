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
import java.net.{URI}




object MainService {
  final val LOCATION_MESSAGE = "MainService.LOCATION_MESSAGE"
  final val WS_MESSAGE = "MainService.WS_MESSAGE"

  final val WS_EXTRA = "extra"

  final val DEFAULT_CHANNEL_NAME = "sparta"

  final val PREFS_CHANNEL_NAME = "channel"

  final val PREFS_USER_NAME = "user.name"
  final val PREFS_USER_EMAIL = "user.email"
  final val PREFS_USER_STATUS = "user.status"

  class LocalBinder( val service:MainService ) extends Binder
}

// ------------------------------------------------------------
class MainService extends Service {
  import MainService._

  
  var currentChannel = DEFAULT_CHANNEL_NAME

  var currentCoord:Coordinate = _

  lazy val sharedPreferences = getSharedPreferences( "MainService", Context.MODE_PRIVATE )

  // ------------------------------------------------------------
  // Profile persistence
  // ------------------------------------------------------------
  private def loadUser( coord:Coordinate ):User = {
    User( User.SELF_USER_ID,
      sharedPreferences.getString( PREFS_USER_NAME, "Anonymous" ),
      sharedPreferences.getString( PREFS_USER_EMAIL, "" ),
      sharedPreferences.getString( PREFS_USER_STATUS, "available" ),
      coord, true )
  }
  private def saveUser( user:User ) {
    val editor = sharedPreferences.edit()
    editor.putString( PREFS_USER_NAME, user.name )
    editor.putString( PREFS_USER_EMAIL, user.email )
    editor.putString( PREFS_USER_STATUS, user.status )
    editor.commit()
  }


  // ------------------------------------------------------------
  // WS Client
  // ------------------------------------------------------------
  val client = new Client( new URI("wss://nexus.ws/json-1.0"), Client.ConnectionOption.DEFAULT, new WebSocketEventHandler{
    override def onOpen( client:Client ) {
    }
    override def onMessage( client:Client, text:String ){
      val json = new JSONObject( text )
      val message = Serializer.deserialize( json )
      broadcast( WS_MESSAGE, message )
      message match {
        case subscribedChannel:SubscribedChannel =>
          // TODO: Use publishTo instead
          client.send( Publish( currentChannel, currentCoord ) )
        case _ =>

      }
    }
    override def onError( client:Client, e:Exception ){
      Log.e( "WS Error", "Exception", e )
    }

  })


  // ------------------------------------------------------------
  // Location MGMT
  // ------------------------------------------------------------
  def startLocation(){
    val locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
    val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    currentCoord = Coordinate( lastKnownLocation )

    val currentUser = loadUser( currentCoord )
    broadcast( LOCATION_MESSAGE, currentUser )

    client.send( PublisherUpdate( currentUser.toPublisher ) )
    client.send( SubscribChannel( currentChannel ) )
    client.send( Publish( currentChannel, currentCoord ) )




    val locationListener = new LocationListener {
      override def onLocationChanged(location: Location) {
        currentCoord = Coordinate( location )
        client.send( Publish( currentChannel, currentCoord ) )
        broadcast( LOCATION_MESSAGE, currentCoord )
      }

      override def onStatusChanged(p1: String, p2: Int, p3: Bundle) {
      }

      override def onProviderEnabled(p1: String) {}

      override def onProviderDisabled(p1: String) {}
    }

    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener )
    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener )

  }

  // ------------------------------------------------------------
  // Broadcast MGMT
  // ------------------------------------------------------------
  private def broadcast( messageType:String, message:Serializable ){
    val broadCastIntent = new Intent( messageType )
    broadCastIntent.putExtra( WS_EXTRA, message )
    sendBroadcast( broadCastIntent )
  }


  // ------------------------------------------------------------
  // Service Lifecycle
  // ------------------------------------------------------------
  override def onStartCommand( intent:Intent, flags:Int, startId:Int ) = {
    intent.getAction() match {

      case MainService.WS_MESSAGE =>

        val msg = intent.getSerializableExtra( WS_EXTRA ).asInstanceOf[WSUpMessage]
        client.send( msg )

        msg match {
          case publisherUpdate:PublisherUpdate => saveUser( User( publisherUpdate.data ) )
          case _ =>
        }

      case _ =>
    }

    Service.START_STICKY
  }

  
  
  override def onCreate(){
    super.onCreate()

    currentChannel = sharedPreferences.getString( PREFS_CHANNEL_NAME, DEFAULT_CHANNEL_NAME )
    startLocation()

  }

  
  override def onDestroy(){
    val editor = sharedPreferences.edit()
    editor.putString( PREFS_CHANNEL_NAME, currentChannel )
    editor.commit()

    client.close()
  }


  lazy val binder = new LocalBinder( this )
  override def onBind( intent: Intent) = {
    binder
  }

}
