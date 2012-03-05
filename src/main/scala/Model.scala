package com.spotmint.android

import java.net.URL
import java.security.MessageDigest
import java.io.Serializable
import com.google.android.maps.GeoPoint
import org.json.JSONObject
import android.location.Location
import android.util.Log

// ------------------------------------------------------------
// ------------------------------------------------------------

trait Users {
  final var users = List[User]()

  def currentUser = users.find( _.id == User.SELF_USER_ID )

  def updateUserOrAppendNew( user:User ){
    var found = false
    users = users.map{ u => if( u.id == user.id ){ found = true;  user} else u }
    if( !found ) users = user :: users
  }

  def updatePublisherByIdOrAppendNew( id:Int, publisher:Publisher ){
    var found = false
    users = users.map{ u => if( u.id == id ){ found = true;  u.update( publisher )} else u }
    if( !found ) users = new User( id, publisher.name, publisher.email, publisher.status ) :: users
  }

  def removeById( id:Int ){ users = users.filterNot( _.id == id ) }

  def updateCoord( id:Int, coord:Coordinate ){
    users = users.map{ user => if( user.id == id ) user.update( coord ) else user }
  }

  def clearAndKeepCurrentUser() {
    users = currentUser.toList
  }

}

object User {
  final val SELF_USER_ID = -1

  def apply( publisher:Publisher ):User = User( SELF_USER_ID, publisher.name, publisher.email, publisher.status )

  def getAvatarURL( email:String, size: Int) = {

    if( email != null && email.length >  0 ){
      Log.v( "SpotMint", "Gravatar email " + email )
      val md5Digest = MessageDigest.getInstance("MD5")
      val in = email.trim.toLowerCase.getBytes
      md5Digest.update(in, 0, in.length)
      val messageDigest = md5Digest.digest()
      val hexString = new StringBuffer()
      messageDigest.foreach {
        b =>
          val hex=Integer.toHexString(0xff & b)
          if(hex.length()==1) hexString.append('0')
          hexString.append(hex)
      }
      new URL("http://gravatar.com/avatar/%s?s=%d" format(hexString.toString, size))
    }
    else
      new URL("http://gravatar.com/avatar/00000000000000000000000000000000?s=%d" format(size) )
  }

}

case class User(id: Int, name: String, email: String, status: String, coord: Coordinate = Coordinate.NO_COORDINATE, var tracked: Boolean = false)
  extends Serializable {

  def toPublisher: Publisher = Publisher(name, email, status)
  def update( newName: String, newEmail: String, newStatus: String ) = User( id, newName, newEmail, newStatus, coord, tracked )
  def update( newCoord:Coordinate ) = User( id, name, email, status, newCoord, tracked )
  def update( publisher:Publisher ) = User( id, publisher.name, publisher.email, publisher.status, coord, tracked )
  def getAvatarURL(size: Int) = User.getAvatarURL( email, size )

  def publisher = Publisher( name, email, status )
}



// ------------------------------------------------------------
// ------------------------------------------------------------

object Coordinate {
  implicit def coordinateToGeoPoint( coord:Coordinate ) = new GeoPoint( (coord.lat * 1E6).toInt, (coord.lng * 1E6).toInt )

  val NO_COORDINATE = Coordinate( 0, 0, 0 )

  def apply( json:JSONObject ):Coordinate = Coordinate( json.getDouble("lat"), json.getDouble("lng"), json.getDouble("acc") )
  def apply(lat: Double, lng: Double, acc: Double): Coordinate = Coordinate(lat, lng, acc, None, None, None, None)
  def apply( location:Location ):Coordinate =
    if( location != null ) Coordinate( location.getLatitude, location.getLongitude, location.getAccuracy )
    else NO_COORDINATE

}

case class Coordinate(lat: Double, lng: Double, acc: Double, alt: Option[Double], altAcc: Option[Double], heading: Option[Double], speed: Option[Double]) extends Serializable {
  def isUndefined = lat == 0 && lng == 0
}

// ------------------------------------------------------------
// WS Message
// ------------------------------------------------------------

object Publisher{
  def apply( json:JSONObject ):Publisher = Publisher( json.getString("name"), json.getString("email"), json.getString("status") )
}
case class Publisher(name: String, email: String, status: String)

trait WSMessage extends Serializable
trait WSDownMessage extends WSMessage
trait WSUpMessage extends WSMessage

case class Bound(session: String) extends WSDownMessage


case class PublisherUpdate(data: Publisher) extends WSUpMessage
case class PublisherUpdated(channel: String, pubId: Int, data: Publisher) extends WSDownMessage

case class CreateChannel() extends WSUpMessage
case class ChannelCreated(channel: String) extends WSDownMessage

case class SubscribChannel(channel: String) extends WSUpMessage
case class SubscribedChannel(channel: String, pubId: Int, data: Publisher) extends WSDownMessage

case class UnsubscribChannel(channel: String) extends WSUpMessage
case class UnsubscribedChannel(channel: String, pubId: Int) extends WSDownMessage

case class Publish(channel: String, data: Coordinate) extends WSUpMessage
case class PublishTo(channel: String, pubIds: Seq[Int], data: Coordinate) extends WSUpMessage

case class Published(channel: String, pubId: Int, data: Coordinate) extends WSDownMessage


// ------------------------------------------------------------
// End WS Message
// ------------------------------------------------------------

