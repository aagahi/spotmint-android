package com.spotmint.android

import java.net.URL
import java.security.MessageDigest
import java.io.Serializable
import com.google.android.maps.GeoPoint
import org.json.JSONObject
import android.location.Location

// ------------------------------------------------------------
// ------------------------------------------------------------

object User {
  final val SELF_USER_ID = -1

  def apply( publisher:Publisher ):User = User( SELF_USER_ID, publisher.name, publisher.email, publisher.status )
}

case class User(id: Int, name: String, email: String, status: String, var coord: Coordinate = Coordinate.NO_COORDINATE, var tracked: Boolean = false)
  extends Serializable {

  def toPublisher: Publisher = Publisher(name, email, status)

  def getAvatarURL(size: Int) = {
    val md5Digest = MessageDigest.getInstance("MD5")
    md5Digest.update(email.getBytes(), 0, email.length())
    val messageDigest = md5Digest.digest()
    val hexString = new StringBuffer()
    messageDigest.foreach {
      b => hexString.append(Integer.toHexString(0xFF & b))
    }
    new URL("http://gravatar.com/avatar/%s?s=%d" format(hexString.toString, size))
  }
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

case class Coordinate(lat: Double, lng: Double, acc: Double, alt: Option[Double], altAcc: Option[Double], heading: Option[Double], speed: Option[Double]) extends Serializable

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

