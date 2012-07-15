package com.spotmint.android

object RunningState {
  final val NONE = new RunningState
  final val RUNNING = new RunningState
  final val KILLING = new RunningState
  final val DYING = new RunningState
}

class RunningState

trait RunningStateAware {

  import RunningState._

  var currentState = NONE

  def state = synchronized {
    currentState
  }

  def state_=(state: RunningState) {
    currentState = state
  }

}