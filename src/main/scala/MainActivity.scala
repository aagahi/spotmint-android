package com.spotmint.android

import _root_.com.google.android.maps._


import android.content._
import android.os.Bundle
import android.graphics.Color
import android.view._
import animation.AnimationUtils
import com.google.android.maps.MapView
import android.util.{AttributeSet, Log}
import android.view.GestureDetector.{OnGestureListener, OnDoubleTapListener}
import android.widget._
import android.widget.TextView.OnEditorActionListener
import android.view.View.{OnFocusChangeListener, OnClickListener}

import android.graphics.drawable.BitmapDrawable
import inputmethod.{EditorInfo}
import android.content.Context._
import android.webkit.WebView


// ------------------------------------------------------------
// ------------------------------------------------------------
// ------------------------------------------------------------
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


// ------------------------------------------------------------
// ------------------------------------------------------------
// ------------------------------------------------------------
object MainActivity {
  final val TAG = "SpotMint Activity"

  final val PREFS_ZOOM_LEVEL = "zoom_level"

}
class MainActivity extends MapActivity with TypedActivity with RunningStateAware with Users {
  import MainActivity._
  import Coordinate.coordinateToGeoPoint

  implicit val context = this

  var mapView:GestureMapView = _
  var mapController:MapController = _


  val overlay = new CustomOverlay( this )
  overlay.onZoomLevelChanged = { zoomLevel =>
    val edit = sharedPreferences.edit()
    edit.putInt( PREFS_ZOOM_LEVEL, zoomLevel )
    edit.commit()
  }

  override def isRouteDisplayed = false

  var peersView:ListView = _
  var peersPopup:PopupWindow = null


  lazy val sharedPreferences = getSharedPreferences( "MainActivity", Context.MODE_PRIVATE )

  // ------------------------------------------------------------
  // Receiver
  // ------------------------------------------------------------
  lazy val receiver = new BroadcastReceiver(){
    def onReceive( context:Context, intent:Intent ){


      val extra = intent.getSerializableExtra(MainService.WS_EXTRA)
      
      Log.v( TAG, "Extra %s / Action %s" format ( extra.toString, intent.getAction ))
      intent.getAction match {
        case MainService.WS_MESSAGE =>
          extra match {
            case SubscribedChannel( channel, pubId, publisher) =>
              updatePublisherByIdOrAppendNew( pubId, publisher )
              updateUI()

            case UnsubscribedChannel( _, pubId) =>
              removeById( pubId )
              updateUI()

            case Published( _, pubId, data ) =>
              updateCoord( pubId, data )
              updateUI( false )


            case PublisherUpdated( _, pubId, data) =>
              updatePublisherByIdOrAppendNew( pubId, data )
              updateUI()


            case _ =>
          }
          

        case MainService.USER_MESSAGE =>
          updateUserOrAppendNew( extra.asInstanceOf[User] )
          updateUI()

        case MainService.CHANNEL_MESSAGE =>
          val channelName = extra.asInstanceOf[String]
          val channelButton = findView(TR.channel_button)
          channelButton.setText( "#"+ channelName )
          
          if( channelName == MainService.DEFAULT_CHANNEL_NAME ){
            val textGlowAnim = AnimationUtils.loadAnimation(context, R.anim.text_glow )
            channelButton.startAnimation( textGlowAnim )
          }


          clearAndKeepCurrentUser()
          updateUI()

        case MainService.LOCATION_MESSAGE =>
          updateCoord( User.SELF_USER_ID, extra.asInstanceOf[Coordinate] )
          updateUI()

      }
    }
  }

  def sendWSMessageToService( message:WSUpMessage ){
    val serviceIntent = new Intent( context, classOf[MainService] )
    serviceIntent.setAction( MainService.WS_MESSAGE )
    serviceIntent.putExtra( MainService.WS_EXTRA, message )
    startService( serviceIntent )
  }

  def sendBackgroundPolicyToService( message:Int ){
    val serviceIntent = new Intent( context, classOf[MainService] )
    serviceIntent.setAction( MainService.BACKGROUND_POLICY_MESSAGE )
    serviceIntent.putExtra( MainService.WS_EXTRA, message )
    startService( serviceIntent )
  }



  // ------------------------------------------------------------
  // Marker mgmt
  // ------------------------------------------------------------

  private def updateUI( reloadLoadListView:Boolean = true ){
    users.find( _.tracked ).foreach{ user => if( !user.coord.isUndefined ) mapController.animateTo( user.coord ) }
    if( reloadLoadListView ) reloadPeersViewAdapter()
    findView(TR.peers_button).setText( users.length.toString )
    mapView.invalidate()
  }


  // ------------------------------------------------------------
  // View Lifecycle
  // ------------------------------------------------------------

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    Log.v( TAG, "onCreate -----------------------------------" )

    state = RunningState.RUNNING

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

    mapController.setZoom( sharedPreferences.getInt( PREFS_ZOOM_LEVEL, 14 ) )



    // Channel Button ------------------------------------
    val channelButton = findView(TR.channel_button)
    channelButton.setOnClickListener( new View.OnClickListener{
      
      def onClick( v:View ){
        channelButton.clearAnimation()
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

        def update() = {
          val name = editViewChannelName.getText.toString
          sendWSMessageToService( SubscribChannel( name ))
          pw.dismiss()
          true
        }

        editViewChannelName.setOnEditorActionListener( new OnEditorActionListener(){
          def onEditorAction( textView: TextView, actionId: Int, e: KeyEvent) = {
            if( actionId == EditorInfo.IME_ACTION_DONE ){ update() }
            else false
          }
        })

        v.findView(TR.channel_clean_button).setOnClickListener( new View.OnClickListener{
          def onClick( view:View ){ editViewChannelName.setText( "" ); true }
        })

        v.findView(TR.channel_update_button).setOnClickListener( new View.OnClickListener{
          def onClick( view:View ){ update() }
        })


      }
    })

    // Peers Button ------------------------------------


    findView(TR.peers_button).setOnClickListener( new View.OnClickListener{

      def onClick( v:View ){
        if( peersPopup == null ){
          peersView = getLayoutInflater.inflate( R.layout.peers, null ).asInstanceOf[ListView]

          reloadPeersViewAdapter()

          peersPopup = new PopupWindow( peersView, 160, mapView.getHeight-44, false )
          peersPopup.setBackgroundDrawable(new BitmapDrawable())

          peersPopup.setAnimationStyle(android.R.style.Animation_Translucent)
          peersPopup.showAtLocation( mapView, Gravity.RIGHT|Gravity.BOTTOM, 2, 2 )
          v.setSelected( true )
        }
        else {
          removePeerPopup()
        }
      }
    })
  }



  // ------------------------------------------------------------
  private def removePeerPopup() {
    peersPopup.dismiss()
    peersPopup = null
    peersView = null
    findView(TR.peers_button).setSelected( false )
  }

  // ------------------------------------------------------------
  // Handle back button in case peer popup is visible
  override def onKeyDown(keyCode:Int, event:KeyEvent ) = {
    if( peersPopup != null && keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
      removePeerPopup()
      true
    }
    else {
      super.onKeyDown( keyCode, event )
    }
  }


  // ------------------------------------------------------------
  // ------------------------------------------------------------
  override def onPause(){
    super.onPause()
    Log.v( TAG, "onPause -----------------------------------" )

    if( state == RunningState.RUNNING )
      sendBackgroundPolicyToService( MainService.LOW_POWER_USAGE )
  }

  // ------------------------------------------------------------
  // ------------------------------------------------------------
  override def onResume(){
    super.onResume()
    Log.v( TAG, "onResume -----------------------------------" )

    // Deal with outside intent with app uri
    val intent = getIntent()
    if( Intent.ACTION_VIEW == intent.getAction() ){
      val uri = intent.getData().toString

      val i = uri.lastIndexOf("#")
      if( i > 0 ) sendWSMessageToService( SubscribChannel( uri.substring( i+1 ) ) )

    }

    sendBackgroundPolicyToService( MainService.HIGH_POWER_USAGE )
  }

  // ------------------------------------------------------------
  // ------------------------------------------------------------
  override def onDestroy(){
    Log.v( TAG, "onDestroy -----------------------------------" )
    super.onDestroy();
    state = RunningState.DYING

    unregisterReceiver( receiver )
  }


  // ------------------------------------------------------------
  // Menu Mgmt
  // ------------------------------------------------------------
  
  
  private def updateProfile( pw:PopupWindow, v:TypedViewHolder ){

    currentUser.foreach{ user =>
      updateUserOrAppendNew( user.update(  v.findView(TR.profile_name).getText.toString,
                                           v.findView(TR.profile_email).getText.toString,
                                           v.findView(TR.profile_status).getText.toString )
      )
    }

    currentUser.foreach{ user =>
      sendWSMessageToService( PublisherUpdate( user.publisher ) )
      overlay.clearUserBitmap( user )
      updateUI()
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
      // ------------------------------------------------------------
      case R.id.menu_about =>
        val webview = new WebView( context )
        webview.setBackgroundColor( Color.argb( 180, 0,0,0 ) )
        webview.loadUrl( getString( R.string.about_url ) )
        val pw = new PopupWindow( webview, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true )
        pw.setBackgroundDrawable(new BitmapDrawable())
        pw.setOutsideTouchable(true)
        pw.setAnimationStyle( android.R.style.Animation_Dialog )
        pw.showAtLocation( mapView, Gravity.CENTER, 0, 0 )

        true

      // ------------------------------------------------------------
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

      // ------------------------------------------------------------
      case R.id.menu_share =>
        val shareIntent = new Intent(Intent.ACTION_SEND)
        shareIntent.setType("text/plain")
        val currentChannel = findView(TR.channel_button).getText
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject) format( currentChannel ) )
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString( R.string.share_text) format ( currentChannel, currentChannel, currentChannel ) )

        context.startActivity(shareIntent)

        true

      // ------------------------------------------------------------
      case R.id.menu_signout =>
        state = RunningState.DYING
        val serviceIntent = new Intent( context, classOf[MainService] )
        val status = stopService( serviceIntent )
        Log.v( TAG, "Activity has stopped service: " + status )
        finish()

        true


      case _ => false
    }

  }


  // ------------------------------------------------------------
  // Peers Adapter
  // ------------------------------------------------------------
  private def reloadPeersViewAdapter() {
    if( peersView != null ){
      peersView.setAdapter( new PeersAdapter( R.layout.peers_row, users.toArray ) )
    }
  }
  class PeersAdapter( resourceId:Int, peers:Array[User])(implicit context:Context)
    extends ArrayAdapter[User]( context, resourceId, peers ) {

    var trackedView:Option[View] = None

    
    override def getView( position:Int, convertView:View, parent:ViewGroup  ):View = {
      val view =  if( convertView != null ) convertView
                  else getLayoutInflater.inflate( R.layout.peers_row, parent, false )
      val user = getItem( position )

      if( user.tracked ){
        trackedView = Some(view)
        view.setBackgroundResource( R.drawable.peer_selected_background )
      } 
      else view.setBackgroundColor( Color.TRANSPARENT )


      view.setOnClickListener( new OnClickListener{
        override def onClick( view:View  ){
          trackedView.foreach( _.setBackgroundColor( Color.TRANSPARENT ) )

          user.tracked = !user.tracked
          trackedView = if( user.tracked ) Some(view) else None

          if( user.tracked ) view.setBackgroundResource( R.drawable.peer_selected_background )
          else view.setBackgroundColor( Color.TRANSPARENT )

          for( u <- users; if( u != user ) ) u.tracked = false

          updateUI( false )
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
