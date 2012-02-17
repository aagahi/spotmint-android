package com.spotmint.android

import _root_.com.google.android.maps._

import android.util.Log

import android.content._
import android.os.Bundle
import android.graphics.Color
import android.widget.{PopupWindow, ArrayAdapter, ListView}
import android.view._
import android.view.View.OnClickListener


class MainActivity extends MapActivity with TypedActivity {
  import Coordinate.coordinateToGeoPoint
  implicit val context = this

  var mapView:MapView = _
  var mapController:MapController = _
  val overlay = new CustomOverlay()

  var peersView:ListView = _

  // ------------------------------------------------------------
  // Receiver
  // ------------------------------------------------------------
  lazy val receiver = new BroadcastReceiver(){
    def onReceive( context:Context, intent:Intent ){


      val extra = intent.getSerializableExtra(MainService.WS_EXTRA)
      intent.getAction() match {
        case MainService.WS_MESSAGE =>
          extra match {
            case subscribChannel:SubscribedChannel =>
              val publisher = subscribChannel.data
              addMarker( new User( subscribChannel.pubId, publisher.name, publisher.email, publisher.status ) )
            case unsubscribChannel:UnsubscribedChannel =>
              removeMarker( unsubscribChannel.pubId )
            case published:Published =>
              updateMarkerCoordById( published.pubId, published.data )
            case _ =>
          }

        case MainService.LOCATION_MESSAGE =>
          extra match {
            case user:User => addMarker( user )
            case coord:Coordinate => updateMarkerCoordById( User.SELF_USER_ID, coord )
          }

      }
    }
  }

  def sendToService( message:WSUpMessage ){
    val serviceIntent = new Intent( context, classOf[MainService] )
    serviceIntent.setAction( MainService.WS_MESSAGE )
    serviceIntent.putExtra( MainService.WS_EXTRA, message )
    startService( serviceIntent )
  }



  // ------------------------------------------------------------
  // Marker mgmt
  // ------------------------------------------------------------

  private def updateMap(){
    overlay.users.find( _.tracked ).foreach{ user => mapController.animateTo( user.coord ) }
    mapView.invalidate()
  }
  
  private def addMarker( user:User ){
    overlay.addUser(user)

    if( peersView != null ) peersView.setAdapter( new PeersAdapter( R.layout.peers_row, overlay.users.toArray ) )

    findView(TR.profilesButton).setText( overlay.users.size.toString )

    ImageLoader.load( user.getAvatarURL( 40 ) ){ overlay.userBitmap( user, _ ) }

    updateMap()
  }

  private def removeMarker( id:Int ){
    overlay.removeUserById( id )

    if( peersView != null ) peersView.setAdapter( new PeersAdapter( R.layout.peers_row, overlay.users.toArray ) )

    findView(TR.profilesButton).setText( overlay.users.size.toString )
    
    updateMap()
  }

  private def updateMarkerCoordById( id:Int, coord:Coordinate ){    
    overlay.users.find( _.id == id ).foreach( _.coord = coord )
    updateMap()
  }
  



  override def isRouteDisplayed = false

  // ------------------------------------------------------------
  // View Lifecycle
  // ------------------------------------------------------------

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    mapView = findView(TR.mapview)
    mapView.setBuiltInZoomControls( true )
    mapView.setStreetView(true)
    mapView.getOverlays().add(overlay)

    mapController = mapView.getController()
    mapController.setZoom(14)

    registerReceiver( receiver, new IntentFilter( MainService.WS_MESSAGE ) )
    registerReceiver( receiver, new IntentFilter( MainService.LOCATION_MESSAGE ) )

    val serviceIntent = new Intent( context, classOf[MainService] )
    startService( serviceIntent )



    val traceButton= findView(TR.traceButton)
    traceButton.setOnClickListener( new View.OnClickListener{
      def onClick( v:View ){
      }
    })

    val profileButton = findView(TR.profilesButton)

    var pw:PopupWindow = null

    profileButton.setOnClickListener( new View.OnClickListener{

      def onClick( v:View ){
        if( pw == null ){
          peersView = getLayoutInflater.inflate( R.layout.peers, null ).asInstanceOf[ListView]

          peersView.setAdapter( new PeersAdapter( R.layout.peers_row, overlay.users.toArray ) )

          pw = new PopupWindow( peersView, 160, mapView.getHeight-44, false )
          pw.showAtLocation( findViewById( R.id.mapview ), Gravity.RIGHT|Gravity.BOTTOM, 2, 2 )
          pw.setAnimationStyle(android.R.style.Animation_Dialog)

        }
        else {
          pw.dismiss()
          pw = null
          peersView = null
        }
        v.setSelected( !v.isSelected )
      }
    })

  }

  // ------------------------------------------------------------
  // ------------------------------------------------------------
  override def onDestroy(){
    super.onDestroy();
    unregisterReceiver( receiver )
  }


  // ------------------------------------------------------------
  // Menu Mgmt
  // ------------------------------------------------------------
  override def onCreateOptionsMenu( menu:Menu ) = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.menu, menu )
    true
  }

  override def onOptionsItemSelected( item:MenuItem ) =
  {
    item.getItemId() match {
      case R.id.menu_about =>
        true
      case R.id.menu_profile =>
        true

      case _ => false
    }

  }


  // ------------------------------------------------------------
  // Peers Adapter
  // ------------------------------------------------------------

  class PeersAdapter( resourceId:Int, peers:Array[User])(implicit context:Context)
    extends ArrayAdapter[User]( context, resourceId, peers ) {

    var trackedView:Option[View] = None
    final val TRACKED_COLOR = Color.parseColor( "#80800000" )
    
    
    override def getView( position:Int, convertView:View, parent:ViewGroup  ):View = {
      val view =  if( convertView != null ) convertView
                  else getLayoutInflater.inflate( R.layout.peers_row, parent, false )
      val user = getItem( position )

      if( user.tracked ){
        trackedView = Some(view)
        view.setBackgroundColor( TRACKED_COLOR )
      } 
      else view.setBackgroundColor( Color.TRANSPARENT )


      view.setOnClickListener( new OnClickListener{
        override def onClick( view:View  ){
          trackedView.foreach( _.setBackgroundColor( Color.TRANSPARENT ) )

          user.tracked = !user.tracked
          trackedView = if( user.tracked ) Some(view) else None

          view.setBackgroundColor(  if( user.tracked ) TRACKED_COLOR else Color.TRANSPARENT )
          for( u <- overlay.users; if( u != user ) ) u.tracked = false

          updateMap()
        }

      })

      val v = TypedResource.view2typed( view )


      val image = v.findView(TR.row_image)

      image.setImageBitmap( null )
      ImageLoader.load( user.getAvatarURL(30) ){ bitmap =>
        image.setImageBitmap( bitmap )
      }

      val nameTextView = v.findView(TR.row_name)
      nameTextView.setText( user.name )

      val statusTextView = v.findView(TR.row_status)
      statusTextView.setText( user.status )



      view
    }

  }

}
