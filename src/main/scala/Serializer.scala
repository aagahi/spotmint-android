package com.spotmint.android

import org.json.JSONObject
import java.util.Locale

object Serializer {
  
  implicit def serialize( message:WSUpMessage ):String =
    message match {
      case PublisherUpdate( data ) => """{"PublisherUpdate":{"data":{"profile":{"name":"%s","email":"%s","status":"%s"}}}}""" formatLocal( Locale.US, data.name, data.email, data.status )
      case CreateChannel() => """{"CreateChannel":{}}"""
      case SubscribChannel( channel ) => """{"SubscribChannel":{"channel":"%s"}}""" format ( channel )
      case UnsubscribChannel(channel) => """{"UnsubscribChannel":{"channel":"%s"}}""" format ( channel )
      case Publish(channel, data) => """{"Publish":{"channel":"%s","data":{"coord":{"lat":%f,"lng":%f,"acc":%f}}}}""" formatLocal ( Locale.US, channel, data.lat, data.lng, data.acc )
      //case PublishTo(channel, pubIds, data) => ""
    }

  def deserialize( text:String ):WSDownMessage = {
    val jsonMessage = new JSONObject( text )
    val name = jsonMessage.keys().next().asInstanceOf[String]
    val json =jsonMessage.getJSONObject( name )
    name match {
      case "Bound" => Bound( json.getString("session") )
      case "PublisherUpdated" => PublisherUpdated( json.getString("channel"), json.getInt("pubId"), Publisher( json.getJSONObject("data").getJSONObject("profile") ) )
      case "ChannelCreated" => ChannelCreated( json.getString( "channel" ) )
      case "SubscribedChannel" =>
        val publisher = if( json.has("data") ) Some( Publisher(json.getJSONObject("data").getJSONObject("profile")) ) else None
        SubscribedChannel( json.getString("channel"), json.getInt("pubId"), publisher )
      case "UnsubscribedChannel" => UnsubscribedChannel( json.getString("channel"), json.getInt("pubId") )
      case "Published" => Published(  json.getString("channel"), json.getInt("pubId"), Coordinate( json.getJSONObject("data").getJSONObject("coord") ), json.getLong("timestamp") )
    }
  }
}
