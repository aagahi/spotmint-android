package com.spotmint.android

import android.os.{Message, Handler}
import java.net.URL
import android.graphics.{BitmapFactory, Bitmap}
import collection.mutable.Queue
import java.security.MessageDigest
import java.math.BigInteger
import android.content.Context
import java.io.{OutputStream, InputStream, FileOutputStream, File}
import android.util.Log

// ------------------------------------------------------------
// Handler
// ------------------------------------------------------------

class ImageLoaderHandler( val url:URL, cacheDir:File, callback:Bitmap => Unit ) extends Handler {
  override def handleMessage( message:Message ){
    callback( message.obj.asInstanceOf[Bitmap] )
  }
  def cacheFile:File = {
    val urlStr = url.toString
    val md5Digest = MessageDigest.getInstance( "MD5" )
    md5Digest.update( urlStr.getBytes, 0, urlStr.length() )
    val i = new BigInteger( 1, md5Digest.digest() )
    String.format( "%1$032X", i )
    new File( cacheDir, "cache-" + String.format( "%1$032X", i ) )
  }
}

// ------------------------------------------------------------
// ------------------------------------------------------------
// ------------------------------------------------------------

object ImageLoader {

  final val loader = new ImageLoaderThread()
  loader.start()

  
  def load( url:URL )( callback: Bitmap => Unit )( implicit context:Context ){
    loader.enqueue( new ImageLoaderHandler( url, context.getCacheDir, callback ) )
  }


  // ------------------------------------------------------------
  // Loader Thread
  // ------------------------------------------------------------
  class ImageLoaderThread extends Thread {
    val MAX_BITMAPCACHE_SIZE = 10
    val fileCacheTimeout = 24*3600*1000

    val queue = new Queue[ImageLoaderHandler]
    var bitmapCache = List[(URL,Bitmap)]()
    
    
    def enqueue( handler:ImageLoaderHandler ){
      synchronized{
        queue.enqueue( handler )
        notify()
      }
    }
    
    override def run(){
      while ( true ) {
        val handler = synchronized{
          if( queue.size == 0 ) wait()
          queue.dequeue()
        }
        Log.v("SpotMint", "Dequeue image to load " + handler.url )

        val bitmap = bitmapCache.find( _._1 == handler.url ) match {
          case Some( tuple ) =>
            bitmapCache = bitmapCache.filterNot( _ == tuple )
            Some( tuple._2 )
          case _ =>
            loadBitmap( handler )
        }

        bitmap.foreach{ bitmap =>
          if( bitmapCache.size > MAX_BITMAPCACHE_SIZE ){
            bitmapCache = bitmapCache.take( MAX_BITMAPCACHE_SIZE - 1 )
          }
          bitmapCache = (handler.url, bitmap ) :: bitmapCache
          handler.sendMessage( handler.obtainMessage( 1, bitmap ) )
        }

      }

    }

    
    private def loadBitmap( handler:ImageLoaderHandler ):Option[Bitmap] = {
      val connection = handler.url.openConnection()
      connection.setConnectTimeout( 5*1000 )
      connection.setReadTimeout( 5*1000 )

      val now = System.currentTimeMillis()

      val file = handler.cacheFile
      val tempFile = new File( file.getParentFile, file.getName+".tmp" )
      try {
        if( !file.exists() || now > file.lastModified()+ fileCacheTimeout ){
          if( tempFile.exists() ) tempFile.delete()
          copyAndClose( connection.getInputStream, new FileOutputStream( tempFile ) )
          if( file.exists() ) file.delete()
          tempFile.renameTo( file )
        }
      }
      catch {
        case _ =>
          tempFile.delete()
      }

      if( file.exists() ){
        Some( BitmapFactory.decodeFile( file.getAbsolutePath ) )
      }
      else None

    }    
    
    private final val IO_BUFFER_SIZE = 1024
    private final val buffer = new Array[Byte](IO_BUFFER_SIZE)

    private def copyAndClose( in:InputStream, out:OutputStream  ){
      try {
        var read = 0
        while ( read != -1 ){
          out.write( buffer, 0, read )
          read = in.read( buffer )
        }
      }
      finally {
        in.close()
        out.close()      
      }
    }

  }
  // ------------------------------------------------------------
  // ------------------------------------------------------------
  // ------------------------------------------------------------


}
