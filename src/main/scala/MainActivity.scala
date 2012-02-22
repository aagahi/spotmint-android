package com.spotmint.android

import _root_.com.google.android.maps._


import android.content._
import android.os.Bundle
import android.graphics.Color
import android.view._
import com.google.android.maps.MapView
import android.util.{AttributeSet, Log}
import android.view.GestureDetector.{OnGestureListener, OnDoubleTapListener}
import android.widget._
import android.widget.TextView.OnEditorActionListener
import android.view.View.{OnFocusChangeListener, OnClickListener}

import android.graphics.drawable.BitmapDrawable
import inputmethod.{InputMethodManager, EditorInfo}


class GestureMapView( context:Context, attrs:AttributeSet ) extends MapView( context, attrs ){
  
  val gestureDetector = new GestureDetector( new OnGestureListener{
    def onDown(e: MotionEvent) = false
    def onShowPress(e: MotionEvent) {}
    def onSingleTapUp(e: MotionEvent) = false
    def onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) = false
    def onLongPress(e: MotionEvent) {}
    def onFling(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) = false
  })
  override def onTouchEvent(e:MotionEvent) = {
    if( gestureDetector.onTouchEvent(e) ) true
    else super.onTouchEvent(e)
  }
}


class MainActivity extends MapActivity with TypedActivity {
  import Coordinate.coordinateToGeoPoint
  implicit val context = this

  var mapView:GestureMapView = _
  var mapController:MapController = _
  val overlay = new CustomOverlay

  var currentUser:Option[User] = None
  
  var peersView:ListView = _

  // ------------------------------------------------------------
  // Receiver
  // ------------------------------------------------------------
  lazy val receiver = new BroadcastReceiver(){
    def onReceive( context:Context, intent:Intent ){


      val extra = intent.getSerializableExtra(MainService.WS_EXTRA)
      
      Log.v( "SpotMint", "Extra %s / Action %s" format ( extra.toString, intent.getAction ))
      intent.getAction match {
        case MainService.WS_MESSAGE =>
          extra match {
            case SubscribedChannel( channel, pubId, publisher) =>
              addMarker( new User( pubId, publisher.name, publisher.email, publisher.status ) )

            case UnsubscribedChannel( _, pubId) =>
              removeMarker( pubId )

            case Published( _, pubId, data ) =>
              updateMarker( overlay.userById( pubId ).map( _.update( data ) ) )

            case PublisherUpdated( _, pubId, data) =>
              updateMarker( overlay.userById( pubId ).map( _.update( data ) ) )
              reloadPeersViewAdapter()

            case _ =>
          }
        case MainService.USER_MESSAGE =>
          val user = extra.asInstanceOf[User]
          currentUser = Some(user)
          addMarker( user )

        case MainService.CHANNEL_MESSAGE =>
          findView(TR.channel_button).setText( "#"+extra.asInstanceOf[String] )

        case MainService.LOCATION_MESSAGE =>
          val coord = extra.asInstanceOf[Coordinate]
          updateMarker( overlay.userById( User.SELF_USER_ID ).map( _.update( coord ) ) )

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

    reloadPeersViewAdapter()

    findView(TR.peers_button).setText( overlay.users.size.toString )

    ImageLoader.load( user.getAvatarURL( 40 ) ){ overlay.userBitmap( user, _ ) }

    updateMap()
  }

  private def removePeersMarker(){
    overlay.removePeers()
    findView(TR.peers_button).setText( "1" )
    reloadPeersViewAdapter()
    updateMap()
  }


  private def removeMarker( id:Int ){
    overlay.removeUserById( id )

    reloadPeersViewAdapter()

    findView(TR.peers_button).setText( overlay.users.size.toString )
    
    updateMap()
  }

  private def updateMarker( user:Option[User] ){
    overlay.update( user )
    updateMap()
  }
  



  override def isRouteDisplayed = false

  // ------------------------------------------------------------
  // View Lifecycle
  // ------------------------------------------------------------

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    Log.v( "SpotMint", "Start Activity" )

    setContentView(R.layout.main)


    registerReceiver( receiver, new IntentFilter( MainService.WS_MESSAGE ) )
    registerReceiver( receiver, new IntentFilter( MainService.LOCATION_MESSAGE ) )
    registerReceiver( receiver, new IntentFilter( MainService.USER_MESSAGE ) )
    registerReceiver( receiver, new IntentFilter( MainService.CHANNEL_MESSAGE ) )

    val serviceIntent = new Intent( context, classOf[MainService] )
    startService( serviceIntent )


    mapView = findView(TR.mapview)
    mapView.setBuiltInZoomControls( true )
    mapView.setStreetView(true)
    mapView.getOverlays.add(overlay)


    mapView.gestureDetector.setOnDoubleTapListener( new OnDoubleTapListener{
      def onSingleTapConfirmed(e: MotionEvent) = false
      def onDoubleTap(e: MotionEvent) = { mapController.zoomInFixing( e.getX.toInt, e.getY.toInt ); true }
      def onDoubleTapEvent(e: MotionEvent) = false
    })


    mapController = mapView.getController
    mapController.setZoom(14)



    // Trace Button ------------------------------------
    val traceButton = findView(TR.trace_button)
    traceButton.setOnClickListener( new View.OnClickListener{
      def onClick( v:View ){
      }
    })

    // Channel Button ------------------------------------
    val channelButton = findView(TR.channel_button)
    channelButton.setOnClickListener( new View.OnClickListener{
      
      def onClick( v:View ){
        val channelView = context.getLayoutInflater.inflate( R.layout.channel, null ).asInstanceOf[ViewGroup]
        val v = TypedResource.view2typed( channelView )

        val pw = new PopupWindow( channelView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true )
        pw.setBackgroundDrawable(new BitmapDrawable())
        pw.setOutsideTouchable(true)
        pw.setAnimationStyle( android.R.style.Animation_Dialog )
        pw.showAtLocation( mapView, Gravity.CENTER_HORIZONTAL|Gravity.TOP, 0, 100 )

        val editViewChannelName = v.findView(TR.channel_name)
        editViewChannelName.setText( channelButton.getText.toString.substring( 1 ) )
        editViewChannelName.requestFocusFromTouch()

        def updateChannel(){
          val name = editViewChannelName.getText.toString
          channelButton.setText( "#"+ name )
          removePeersMarker()
          sendToService( SubscribChannel( name ))
          pw.dismiss()
        }

        editViewChannelName.setOnEditorActionListener( new OnEditorActionListener(){
          def onEditorAction( textView: TextView, actionId: Int, e: KeyEvent) = {
            if( actionId == EditorInfo.IME_ACTION_DONE ){ updateChannel(); true }
            else false
          }
        })

        v.findView(TR.channel_clean_button).setOnClickListener( new View.OnClickListener{
          def onClick( view:View ){ editViewChannelName.setText( "" ); true }
        })

        v.findView(TR.channel_update_button).setOnClickListener( new View.OnClickListener{
          def onClick( view:View ){ updateChannel(); true }
        })


      }
    })

    // Peers Button ------------------------------------
    val peersButton = findView(TR.peers_button)

    var pw:PopupWindow = null

    peersButton.setOnClickListener( new View.OnClickListener{

      def onClick( v:View ){
        if( pw == null ){
          peersView = getLayoutInflater.inflate( R.layout.peers, null ).asInstanceOf[ListView]

          reloadPeersViewAdapter()

          pw = new PopupWindow( peersView, 160, mapView.getHeight-44, false )
          pw.setAnimationStyle(android.R.style.Animation_Translucent)
          pw.showAtLocation( mapView, Gravity.RIGHT|Gravity.BOTTOM, 2, 2 )

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
  
  
  private def updateProfile( pw:PopupWindow, v:TypedViewHolder ){

    currentUser.foreach{ user =>
      currentUser= Some( user.update(  v.findView(TR.profile_name).getText.toString,
        v.findView(TR.profile_email).getText.toString,
        v.findView(TR.profile_status).getText.toString )
      )
    }

    currentUser.foreach{ user =>
      overlay.update( user )
      sendToService( PublisherUpdate( user.publisher ) )
    }

    pw.dismiss()

  }
  
  override def onCreateOptionsMenu( menu:Menu ) = {
    val inflater = getMenuInflater
    inflater.inflate(R.menu.menu, menu )
    true
  }


  override def onOptionsItemSelected( item:MenuItem ) = {
    item.getItemId match {
      case R.id.menu_about =>
        true

      case R.id.menu_profile =>
        val profileView = context.getLayoutInflater.inflate( R.layout.profile, null ).asInstanceOf[RelativeLayout]
        val v = TypedResource.view2typed( profileView )

        val pw = new PopupWindow( profileView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true )
        pw.setBackgroundDrawable(new BitmapDrawable())
        pw.setOutsideTouchable(true)
        pw.setAnimationStyle( android.R.style.Animation_Dialog )
        pw.showAtLocation( mapView, Gravity.CENTER_HORIZONTAL|Gravity.TOP, 0, 100 )


        val image = v.findView(TR.profile_avatar)
        def loadImage( email:String ){
          ImageLoader.load( User.getAvatarURL( email, 80) ){ bitmap =>
            image.setImageBitmap( bitmap )
          }
        }

        val emailView = v.findView(TR.profile_email)
        emailView.setOnFocusChangeListener( new OnFocusChangeListener{
          def onFocusChange( view: View, hasFocus: Boolean) { if( !hasFocus ) loadImage( emailView.getText.toString ) }
        })

        val editViewName = v.findView(TR.profile_name)
        currentUser.foreach{ user =>
          editViewName.setText( user.name )
          v.findView(TR.profile_status).setText( user.status )
          emailView.setText( user.email )
          loadImage( user.email )
        }
        editViewName.requestFocusFromTouch()

        v.findView(TR.profile_status).setOnEditorActionListener( new OnEditorActionListener(){
          def onEditorAction( textView: TextView, actionId: Int, e: KeyEvent) = {
            if( actionId == EditorInfo.IME_ACTION_DONE ){ updateProfile(pw,v); true }
            else false
          }
        })
        v.findView(TR.profile_update_button).setOnClickListener( new View.OnClickListener{
          def onClick( view:View ){ updateProfile(pw,v); true }
        })
        

        true

      case _ => false
    }

  }


  // ------------------------------------------------------------
  // Peers Adapter
  // ------------------------------------------------------------
  private def reloadPeersViewAdapter() {
    if( peersView != null ) peersView.setAdapter( new PeersAdapter( R.layout.peers_row, overlay.users.toArray ) )
  }
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
