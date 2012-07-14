package com.spotmint.android

import java.net.URL
import java.security.MessageDigest
import java.io.Serializable
import com.google.android.maps.GeoPoint
import org.json.JSONObject
import android.location.Location
import android.util.Log
import android.content.Context

// ------------------------------------------------------------
// ------------------------------------------------------------

trait Users {
  final var users = List[User]()
  final var currentUserId = -1

  def currentUser = userById( currentUserId )
  def userById( id:Int ) = users.find( _.id == id )

  def replaceUserBy( olduser:User, newUser:User ){
    users = users.map{ u => if( u == olduser ) newUser else u }
  }

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

  def updateCoord( id:Int, coord:Coordinate, timestamp:Long = System.currentTimeMillis() ){
    users = users.map{ user => if( user.id == id ) user.update( coord, timestamp ) else user }
  }

  def clearAndKeepCurrentUser() {
    users = currentUser.toList
  }

}

object User {
  final val SELF_USER_ID = -1

  def apply( publisher:Publisher ):User = User( 0, publisher.name, publisher.email, publisher.status )


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

case class User(id: Int, name: String, email: String, status: String, coord: Coordinate = Coordinate.NO_COORDINATE, timestamp:Long = 0L, var tracked: Boolean = false)
  extends Serializable {

  def toPublisher: Publisher = Publisher(name, email, status)
  def update( newId: Int ):User = User( newId, name, email, status, coord, timestamp, tracked )
  def update( newName: String, newEmail: String, newStatus: String ) = User( id, newName, newEmail, newStatus, coord, timestamp, tracked )
  def update( publisher:Publisher ) = User( id, publisher.name, publisher.email, publisher.status, coord, timestamp, tracked )
  def update( newCoord:Coordinate, newTimestamp:Long = System.currentTimeMillis() ) ={
    val now = System.currentTimeMillis()
    val ts = if( newTimestamp > now ) now else newTimestamp
    User( id, name, email, status, newCoord, ts, tracked )
  }


  def getAvatarURL(size: Int) = User.getAvatarURL( email, size )

  def publisher = Publisher( name, email, status )

  def updatePositionSinceSec = {
   val now = System.currentTimeMillis()
   ( now - timestamp ) / 1000
  }

  def upadtePositionString(implicit context:Context ) = {
    val sec = updatePositionSinceSec
    if( sec == 0L ) context.getString( R.string.callout_now )
    else if( sec < 60L ) context.getString( R.string.callout_sec_ago ) format sec
    else if( sec/60 < 60L ) context.getString( R.string.callout_min_ago ) format (sec/60)
    else context.getString( R.string.callout_long_time_ago )
  }
}

// ------------------------------------------------------------
// ------------------------------------------------------------

object Coordinate {
  implicit def coordinateToGeoPoint( coord:Coordinate ) = new GeoPoint( (coord.lat * 1E6).toInt, (coord.lng * 1E6).toInt )
  implicit def geoPointToCoodinate( geo:GeoPoint ) = Coordinate( geo.getLatitudeE6.toDouble/1E6, geo.getLongitudeE6.toDouble/1E6, 0.0 )

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
case class SubscribedChannel( channel:String, pubId:Int, data:Option[Publisher] ) extends WSDownMessage

case class UnsubscribChannel(channel: String) extends WSUpMessage
case class UnsubscribedChannel(channel: String, pubId: Int) extends WSDownMessage

case class Publish(channel: String, data: Coordinate) extends WSUpMessage
case class PublishTo(channel: String, pubIds: Seq[Int], data: Coordinate) extends WSUpMessage

case class Published( channel:String, pubId:Int, data:Coordinate, timestamp:Long ) extends WSDownMessage



// ------------------------------------------------------------
// End WS Message
// ------------------------------------------------------------

