package com.spotmint.android


import com.google.android.maps.{MapView, Overlay}
import android.graphics._

object CustomOverlay {
  val SHADOW_COLOR = Color.parseColor( "#80000000")
}
class CustomOverlay( usersHolder:Users ) extends Overlay {
  import Coordinate._


  private var userIdBitmap = Map[Int,Bitmap]()
  private def userBitmap( mapView:MapView, user:User, bitmap:Bitmap ) {
    userIdBitmap = userIdBitmap + ( (user.id)->bitmap ); mapView.invalidate()
  }


  var currentZoomLevel:Int = 0
  var onZoomLevelChanged:Int=>Unit = { _ => }

  def clearUserBitmap( user:User ){
    userIdBitmap -= user.id
  }

  override def draw( canvas:Canvas, mapView:MapView, shadow:Boolean ) {
    val zoomLevel = mapView.getZoomLevel
    if( currentZoomLevel != zoomLevel ){
      currentZoomLevel =  zoomLevel
      onZoomLevelChanged( zoomLevel )
    }
    if( !shadow  ){
      usersHolder.users.foreach{ user =>
        if( !user.coord.isUndefined ) {

          userIdBitmap.get( user.id ) match {
            case Some( bitmap)  =>
              val pixel = mapView.getProjection.toPixels( user.coord, null )

              val x = pixel.x-bitmap.getWidth/2
              val y = pixel.y-bitmap.getHeight
              val w = bitmap.getWidth
              val h = bitmap.getHeight

              val isAccurate = user.coord.acc > 0 && user.coord.acc < 150

              val paint = new Paint()
              if ( isAccurate ){
                paint.setColor( Color.BLACK )
                paint.setStrokeWidth( 1 )
              }  else {
                // inacurate => rgba(150, 0, 0, 0.5)
                paint.setColor( Color.argb( 5*25, 150, 0, 0 ) )
                paint.setStrokeWidth( 2 )
              }
              paint.setStyle( Paint.Style.STROKE )
              paint.setShadowLayer( 4, 2, 2, CustomOverlay.SHADOW_COLOR )
              canvas.drawRect( x, y, x+w, y+h, paint )

              canvas.drawBitmap( bitmap, x, y, null )

              paint.clearShadowLayer()
              canvas.drawRect( x, y, x+w, y+h, paint )

              if( isAccurate ){
                paint.setStyle( Paint.Style.FILL )
                paint.setAntiAlias( true )
                canvas.drawCircle( pixel.x, pixel.y, 3, paint )
              }

            case None =>
              implicit val context = mapView.getContext
              ImageLoader.load( user.getAvatarURL( 40 ) ){ userBitmap( mapView, user, _ ) }

          } // match

        } // if

      } // foreach
    } // shadow
    else {
      usersHolder.users.foreach{ user =>
        if( !user.coord.isUndefined && user.coord.acc > 0 && user.coord.acc < 150 ) {
          val pixel = mapView.getProjection.toPixels( user.coord, null )
          val pointNextXPixel = mapView.getProjection.fromPixels( pixel.x+100, pixel.y )
          val pixelDistance = distance( user.coord, pointNextXPixel )
          val radius = 100.0* user.coord.acc.toDouble/pixelDistance;

          val paint = new Paint()

          // fillColor: '#00C' fillOpacity: 0.4
          paint.setARGB( 4*25, 0, 0, 200 )
          paint.setStyle( Paint.Style.STROKE )
          paint.setAntiAlias( true )
          canvas.drawCircle( pixel.x, pixel.y, radius.toFloat, paint )

          paint.reset()

          // fillColor: '#88F' fillOpacity: 0.1
          paint.setARGB( 25, 136, 136, 255 )
          paint.setStrokeWidth( 1 )
          paint.setStyle( Paint.Style.FILL )
          canvas.drawCircle( pixel.x, pixel.y, radius.toFloat, paint )



        }
      }
    }
  }

  import math._
  private def deg2rad( deg:Double ) = deg * Pi / 180.0
  private def rad2deg( rad:Double ) = rad * 180.0 / Pi

  // in meter
  def distance( from:Coordinate, to:Coordinate ):Double = {
    if( to.lat == from.lat && to.lng == from.lng ) 0.0
    else {
      val theta = from.lng - to.lng
      var dist = sin( deg2rad( from.lat ) ) * sin( deg2rad( to.lat ) ) + cos( deg2rad( from.lat ) ) * cos( deg2rad( to.lat ) ) * cos( deg2rad( theta ) )
      dist = acos(dist)
      dist = rad2deg(dist)
      dist = dist * 60 * 1.1515
      dist = dist * 1.609344*1000.0
      dist
    }
  }

}
