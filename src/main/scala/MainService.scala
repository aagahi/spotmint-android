package com.spotmint.android

import ws.nexus.websocket.client.{WebSocketEventHandler, Client}
import android.util.Log

import Serializer.serialize
import android.content.{Context, Intent}
import java.io.Serializable
import java.net.URI
import util.Random
import android.app.{NotificationManager, PendingIntent, Notification, Service}
import android.location.{Location, LocationListener, LocationManager}
import java.util.{Timer, TimerTask}
import android.os.{Message, Handler, Bundle, Binder}


object MainService {
  
  final val TAG = "SpotMint Service"

  final val WS_MESSAGE = "MainService.WS_MESSAGE"
  final val LOCATION_MESSAGE = "MainService.LOCATION_MESSAGE"
  final val USER_MESSAGE = "MainService.USER_MESSAGE"
  final val CHANNEL_MESSAGE = "MainService.CHANNEL_MESSAGE"
  final val BACKGROUND_POLICY_MESSAGE = "MainService.BACKGROUND_POLICY_MESSAGE"
  final val TRACKING_ID_MESSAGE = "MainService.TRACKING_ID_MESSAGE"
  final val RECONNECTING_MESSAGE = "MainService.RECONNECTING_MESSAGE"

  final val WS_EXTRA = "extra"

  final val LOW_POWER_USAGE = 0
  final val HIGH_POWER_USAGE = 2

  final val PREFS_NAME_KEY = "MainService"

  final val PREFS_CHANNEL_NAME = "channel"

  final val PREFS_USER_NAME = "user.name"
  final val PREFS_USER_EMAIL = "user.email"
  final val PREFS_USER_STATUS = "user.status"
  final val PREFS_SESSION = "session"



  class LocalBinder( val service:MainService ) extends Binder
}

// ------------------------------------------------------------
class MainService extends Service with RunningStateAware {
  import MainService._

  var currentUser:User = _

  lazy val sharedPreferences = getSharedPreferences( PREFS_NAME_KEY, Context.MODE_PRIVATE )
  var currentChannel:String = _
  var currentTrackingUserId:Option[Int] = None


  lazy val settings = getSharedPreferences( SettingsActivity.PREFS_NAME_KEY, Context.MODE_PRIVATE )

  // ------------------------------------------------------------
  // Pref
  // ------------------------------------------------------------
  private def loadUser():User = {
    User( -1,
      sharedPreferences.getString( PREFS_USER_NAME, "Anonymous-" + (100 + Random.nextInt(899)) ),
      sharedPreferences.getString( PREFS_USER_EMAIL, "" ),
      sharedPreferences.getString( PREFS_USER_STATUS, "available" ),
      Coordinate.NO_COORDINATE, 0, true )
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
  // Settings
  // ------------------------------------------------------------
  def reducedGPSAfterMinutes = settings.getString(SettingsActivity.PREFS_REDUCED_GPS_AFTER, "5" ).toInt
  def reducedGPSAccuracyMeters = settings.getString(SettingsActivity.PREFS_REDUCED_GPS_ACCURACY, "10" ).toInt
  def disconnectTimeoutMinutes = settings.getString(SettingsActivity.PREFS_DISCONNECT_TIMEOUT, "60" ).toInt

  // ------------------------------------------------------------
  // WS Client
  // ------------------------------------------------------------

  var currentSession = ""
  final val sessionTimeoutSec = 300
  final val nexusHost = "nexus.ws"

  def nexusURI = new URI("wss://%s/json-1.0/%d/%s" format( nexusHost, sessionTimeoutSec, currentSession ) )

  var susbscribeds = List[SubscribedChannel]()
  var publisheds = List[Published]()

  def clearChannel() {
    susbscribeds = Nil
    publisheds = Nil
  }

  val client = new Client( new WebSocketEventHandler{

    var lastNetworkActivity = 0L
    final val MAX_RECONNECT_SLEEP = 60*1000
    var reconnectSleep = 0L
    
    val networkHandler = new Handler{
      final val ON_MESSAGE = 1
      final val ON_STOP = 2

      override def handleMessage( message:Message ){
        (message.what, message.obj) match {
          case ( ON_MESSAGE, message:WSDownMessage ) =>
            broadcast( WS_MESSAGE, message )
            message match {

              case Bound( session ) =>
                currentSession = session
                saveCurrentSession()

              case subscribedChannel:SubscribedChannel =>
                if( subscribedChannel.data.isEmpty ){
                  currentUser = currentUser.update( subscribedChannel.pubId )
                }
                susbscribeds = subscribedChannel :: susbscribeds

              case UnsubscribedChannel(channel, pubId) =>
                susbscribeds = susbscribeds.filterNot( _.pubId == pubId )
                publisheds = publisheds.filterNot( _.pubId == pubId )

              case PublisherUpdated(channel, pubId, publisher) =>
                susbscribeds = susbscribeds.map{ sub =>
                  if( sub.channel == channel && sub.pubId == pubId ) SubscribedChannel( channel, pubId, Some(publisher) )
                  else sub
                }

              case published:Published =>
                publisheds = published :: publisheds.filterNot( _.pubId == published.pubId )

              case _ =>
            }

          case ( ON_STOP, client:Client ) =>
            clearChannel()
            Log.i( "WS Stop", "Reconnect " + nexusURI.toString )
            if( state == RunningState.RUNNING ){
              new Timer().schedule( new TimerTask {
                def run() {
                  client.connect( nexusURI, Client.ConnectionOption.DEFAULT  )
                  broadcast( RECONNECTING_MESSAGE )
                }
              }, reconnectSleep )
              reconnectSleep = if( reconnectSleep >= MAX_RECONNECT_SLEEP ) reconnectSleep else reconnectSleep + 1000
            }
        } // match
      } // def
    } // Handler

    override def onOpen( client:Client ) {
      reconnectSleep = 0
      client.send( PublisherUpdate( currentUser.toPublisher ) )
      client.send( SubscribChannel( currentChannel ) )
      client.send( Publish( currentChannel, currentUser.coord ) )
    }
    override def onMessage( client:Client, text:String ){
      lastNetworkActivity = System.currentTimeMillis()
      val message = Serializer.deserialize( text )

      networkHandler.sendMessage( networkHandler.obtainMessage( networkHandler.ON_MESSAGE, message ) )
    }
   
    override def onStop( client:Client ){
      networkHandler.sendMessage( networkHandler.obtainMessage( networkHandler.ON_STOP, client ) )

    }
    override def onError( client:Client, t:Throwable ){
      Log.e( "WS Error", "Throwable", t )
    }
    
  })

  

  // ------------------------------------------------------------
  // Location MGMT
  // ------------------------------------------------------------
  var locationManager:LocationManager = _

  lazy val locationTimer = new Timer()

  val locationListener = new LocationListener {
    var bestLocation:Location = _
    override def onLocationChanged(location: Location) {

      if( bestLocation != null ){
        if( location.getAccuracy < bestLocation.getAccuracy ) bestLocation = location
        else if( location.getTime > bestLocation.getTime + 10*1000 ) bestLocation = location
        else if( location.getProvider == bestLocation.getProvider && location.distanceTo( bestLocation ) > 10 ) bestLocation = location
      }
      else bestLocation = location

      if( bestLocation == location ){
        val coord = Coordinate( bestLocation )
        currentUser = currentUser.update( coord )
        client.send( Publish( currentChannel, coord ) )
        broadcast( LOCATION_MESSAGE, Some(coord) )
      }

    }


    override def onStatusChanged( provider: String, status: Int, extras: Bundle) {
      Log.v( TAG, "Location Manager provider %s status changed %d - %s" format( provider, status, extras.toString ) )
    }

    override def onProviderEnabled( provider: String) {
      Log.v( TAG, "Location Manager provider %s enabled" format provider  )
    }

    override def onProviderDisabled( provider: String) {
      Log.v( TAG, "Location Manager provider %s disabled" format provider  )
    }
  }


  def startLocation(){
    locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

    val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    val net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

    val location =  if( gps != null && net != null ){
                      if( gps.getTime > net.getTime ) gps else net
                    } 
                    else if( gps != null ) gps
                    else if( net != null ) net
                    else null
    
    if( location != null ){
      val coord = Coordinate( location )
      currentUser = currentUser.update( coord )
    }

    locationAccuracyHandler.switchHighAccuracy()

    locationTimer.schedule( new TimerTask {
      def run() { client.send( Publish( currentChannel, currentUser.coord ) ) }
    }, 60*1000, 3*60*1000 )
  }

  def stopLocation(){
    locationManager.removeUpdates( locationListener )
    locationTimer.cancel()
  }


  val locationAccuracyHandler = new Handler() {
    final val HIGH_ACCURACY = 1
    final val LOW_ACCURACY = 2

    def switchHighAccuracy(){
      sendMessage(obtainMessage( HIGH_ACCURACY ) )
    }
    def switchLowAccuracy(){
      sendMessage(obtainMessage( LOW_ACCURACY ) )
    }

    override def handleMessage( message:Message ){
      message.what match {
        case HIGH_ACCURACY =>
          Log.v(TAG, "High Accurracy Location"  )
          locationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 0, 5, locationListener )
          locationManager.requestLocationUpdates( LocationManager.NETWORK_PROVIDER, 30*1000, 0, locationListener )

        case LOW_ACCURACY =>
          Log.v(TAG, "Low Accurracy Location"  )
          val accMeters = reducedGPSAccuracyMeters
          if( accMeters <= 100 ){
            locationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 10*1000, reducedGPSAccuracyMeters, locationListener )
          }
          locationManager.requestLocationUpdates( LocationManager.NETWORK_PROVIDER, 30*1000, reducedGPSAccuracyMeters, locationListener )
      }
    }
  }


  var lowPowerTimer:Timer = _
  var disconnectTimer:Timer = _

  @inline private def lowPowerLocationManagement(){
    lowPowerTimer = new Timer()
    lowPowerTimer.schedule( new TimerTask {
      def run() {
        locationManager.removeUpdates( locationListener )
        locationAccuracyHandler.switchLowAccuracy()
      }
    }, reducedGPSAfterMinutes*60*1000 )

    disconnectTimer = new Timer()
    disconnectTimer.schedule( new TimerTask {
      def run() {
        showAutoDisconnectNotitication()
        stopSelf()
      }
    }, disconnectTimeoutMinutes*60*1000 )

  }

  @inline private def highPowerLocationManagement(){
    if( lowPowerTimer != null ) {
      lowPowerTimer.cancel()
      lowPowerTimer.purge()
    }
    if ( disconnectTimer != null ){
      disconnectTimer.cancel()
      disconnectTimer.purge()
    }
    locationManager.removeUpdates( locationListener )
    locationAccuracyHandler.switchHighAccuracy()
  }

  // ------------------------------------------------------------
  // Broadcast MGMT
  // ------------------------------------------------------------
  private def broadcast( messageType:String, message:Serializable ){
    broadcast( messageType, Option(message) )
  }
  private def broadcast( messageType:String, message:Option[Serializable] = None ){
    broadcastIntent( messageType ){ intent =>
      message.foreach( message => intent.putExtra( WS_EXTRA, message ) )
    }
  }

  private def broadcastIntent( messageType:String )( block : Intent => Any ){
    val broadCastIntent = new Intent( messageType )
    block( broadCastIntent )
    Log.v(TAG, "Broadcast %s => %s" format ( messageType, broadCastIntent.getExtras ) )
    sendBroadcast( broadCastIntent )
  }

  // ------------------------------------------------------------
  // Notification bar
  // ------------------------------------------------------------
  val SPOTMINT_NOTIFICATION_ID = 1
  val SPOTMINT_AUTODISCONNECT_ID = 2

  private def notifyMessage(id:Int, contentText:String )( f: Notification => Notification ){
    val tickerText = getString( R.string.notification_ticker )
    val when = System.currentTimeMillis()

    val contentTitle = getString( R.string.notification_title )
    val notification = f( new Notification( R.drawable.notification, tickerText, when ) )

    val notificationIntent = new Intent(this, classOf[MainActivity])
    val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
    notification.setLatestEventInfo( getApplicationContext(), contentTitle, contentText, contentIntent)

    getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager].notify(id, notification)
  }

  private def showBroadcastNotitication(){
    val msg = getString( R.string.notification_text ) format ( "#" + currentChannel )
    notifyMessage( SPOTMINT_NOTIFICATION_ID, msg  ){ notification =>
      notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR
      notification
    }
  }

  private def showAutoDisconnectNotitication(){
    val msg = getString( R.string.notification_autodisconnect_text ) format ( disconnectTimeoutMinutes )
    notifyMessage( SPOTMINT_AUTODISCONNECT_ID, msg ){ notification =>
      notification.vibrate = Array(100L, 200L, 100L, 500L)
      notification.flags = Notification.FLAG_AUTO_CANCEL | Notification.FLAG_SHOW_LIGHTS
      notification.ledARGB =  0xff00ff00
      notification.ledOnMS = 300
      notification.ledOffMS = 5000
      notification
    }
  }

  private def removeNotificationBar(){
    getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager].cancel(SPOTMINT_NOTIFICATION_ID)
  }

  // ------------------------------------------------------------
  // Service Lifecycle
  // ------------------------------------------------------------
  override def onStartCommand( intent:Intent, flags:Int, startId:Int ) = {
    if( intent != null ){
      val action = intent.getAction
      action match {
        case WS_MESSAGE =>
          val msg = intent.getSerializableExtra( WS_EXTRA ).asInstanceOf[WSUpMessage]
          msg match {
            case PublisherUpdate( publisher )=>
              currentUser = currentUser.update( publisher )
              saveUser( currentUser )
              client.send( msg )

            case SubscribChannel( channel ) =>
              if( currentChannel != channel ){
                client.send( UnsubscribChannel( currentChannel ) )
                currentChannel = channel
                clearChannel()
                saveChannel()
                broadcast( CHANNEL_MESSAGE, currentChannel )
                showBroadcastNotitication()
                client.send( msg )
                client.send( Publish( currentChannel, currentUser.coord ) )
              }


            case _ =>
              // TODO: in case of Unsubscrib there is no garanty the server recieve the mesg... if network error occurs client might still be connected to the channel
              // We should find to have a better underlying delivery system
              client.send( msg )
          }



        case BACKGROUND_POLICY_MESSAGE =>
          intent.getIntExtra( WS_EXTRA, LOW_POWER_USAGE ) match {
            case LOW_POWER_USAGE => lowPowerLocationManagement()
            case HIGH_POWER_USAGE => highPowerLocationManagement()
          }


        case TRACKING_ID_MESSAGE =>
          val id = intent.getIntExtra( WS_EXTRA, currentUser.id )
          currentTrackingUserId = Option( id  )
          broadcastIntent( TRACKING_ID_MESSAGE ){ _.putExtra( WS_EXTRA, id  ) }

/*
        case SettingsActivity.PREFS_REDUCED_GPS_AFTER | SettingsActivity.PREFS_REDUCED_GPS_ACCURACY | SettingsActivity.PREFS_DISCONNECT_TIMEOUT =>
*/

        // default start (usually when activity restart)
        case _ =>
          broadcast( USER_MESSAGE, currentUser )
          broadcast( CHANNEL_MESSAGE, currentChannel )
          susbscribeds.foreach( broadcast( WS_MESSAGE, _ ) )
          publisheds.foreach( broadcast( WS_MESSAGE, _ ) )
          currentTrackingUserId.foreach( id => broadcastIntent( TRACKING_ID_MESSAGE ){ _.putExtra( WS_EXTRA, id  ) } )
      }
    }
    Service.START_STICKY
  }



  override def onCreate(){
    super.onCreate()
    state = RunningState.RUNNING

    currentSession = sharedPreferences.getString( PREFS_SESSION, "" )
    currentChannel = sharedPreferences.getString( PREFS_CHANNEL_NAME, getString( R.string.channel_tap_to_change ) )
    currentUser = loadUser()


    showBroadcastNotitication()
    startLocation()
    client.connect( nexusURI, Client.ConnectionOption.DEFAULT, Some( ( t:Throwable ) => { Log.v("SpotMint WS", t.getMessage, t ) } ) )


  }


  override def onDestroy(){
    super.onDestroy()
    client.send( UnsubscribChannel( currentChannel ) )
    state = RunningState.DYING
    removeNotificationBar()
    stopLocation()
    client.close()
  }


  lazy val binder = new LocalBinder( this )
  override def onBind( intent: Intent) = binder


}
