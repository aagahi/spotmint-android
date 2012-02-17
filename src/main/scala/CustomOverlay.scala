package com.spotmint.android


import com.google.android.maps.{MapView, Overlay}
import android.graphics._

class CustomOverlay extends Overlay {
  import Coordinate.coordinateToGeoPoint

  private var userIdBitmap = Map[Int,Bitmap]()
  def userBitmap( user:User, bitmap:Bitmap ) { userIdBitmap = userIdBitmap + ( (user.id)->bitmap ) }

  var users = List[User]()

  def addUser( item:User ){
      users ::= item
  }

  def removeUserById( id:Int ){
    users = users.filterNot( _.id == id )
    userIdBitmap = userIdBitmap - id
  }
  
  
  
  override def draw( canvas:Canvas, mapView:MapView, shadow:Boolean ) {
    if( !shadow  )
      users.foreach{ user => userIdBitmap.get(user.id).foreach{ bitmap =>
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
      }
    }
  }
}
