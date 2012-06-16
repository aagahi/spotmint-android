package com.spotmint.android

import android.os.Handler
import android.view.View

object Utils {

  implicit def onClickListenerFromFunction(action: View => Any) = {
    new View.OnClickListener() {
      def onClick(v: View) {
        action(v)
      }
    }
  }

  def asyncTask[T](task: => Option[T])(callback: T => Any = { _: T => }) {
    val handler = new Handler
    new Thread(new Runnable {
      def run {
        task foreach {
          result =>
            handler post new Runnable {
              def run {
                callback(result)
              }
            }
        }
      }
    }) start

  }


}
