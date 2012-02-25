package com.spotmint.android


import com.google.android.maps.{MapView, Overlay}
import android.graphics._

class CustomOverlay( usersHolder:Users ) extends Overlay {
  import Coordinate.coordinateToGeoPoint


  private var userIdBitmap = Map[Int,Bitmap]()
  private def userBitmap( mapView:MapView, user:User, bitmap:Bitmap ) {
    userIdBitmap = userIdBitmap + ( (user.id)->bitmap ); mapView.invalidate()
  }





  override def draw( canvas:Canvas, mapView:MapView, shadow:Boolean ) {
    if( !shadow  )
      usersHolder.users.foreach{ user =>
        userIdBitmap.get( user.id ) match {
          case Some( bitmap)  =>
            val pixel = mapView.getProjection.toPixels( user.coord, null )

            val x = pixel.x-bitmap.getWidth/2
            val y = pixel.y-bitmap.getHeight
            val w = bitmap.getWidth
            val h = bitmap.getHeight

            val paint = new Paint()
            paint.setColor( Color.BLACK )
            paint.setStyle( Paint.Style.STROKE )
            paint.setShadowLayer( 4, 2, 2, Color.parseColor( "#80000000") )
            canvas.drawRect( x, y, x+w, y+h, paint )

            canvas.drawBitmap( bitmap, x, y, null )

            paint.clearShadowLayer()
            canvas.drawRect( x, y, x+w, y+h, paint )

            paint.setStyle( Paint.Style.FILL )
            paint.setAntiAlias( true )
            canvas.drawCircle( pixel.x, pixel.y, 3, paint )

          case None =>
            implicit val context = mapView.getContext
            ImageLoader.load( user.getAvatarURL( 40 ) ){ userBitmap( mapView, user, _ ) }
        }

      }
  }



}
