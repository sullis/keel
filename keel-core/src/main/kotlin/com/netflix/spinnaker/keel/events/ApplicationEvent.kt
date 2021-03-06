package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Clock
import java.time.Instant

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = JsonTypeInfo.As.PROPERTY
)
@JsonSubTypes(
  JsonSubTypes.Type(value = ApplicationActuationPaused::class, name = "ApplicationActuationPaused"),
  JsonSubTypes.Type(value = ApplicationActuationResumed::class, name = "ApplicationActuationResumed")
)
abstract class ApplicationEvent : PersistentEvent() {
  override val scope = Scope.APPLICATION

  override val uid: String
    get() = application
}

/**
 * Actuation at the application level has been paused.
 *
 * @property reason The reason why actuation was paused.
 */
data class ApplicationActuationPaused(
  override val application: String,
  override val timestamp: Instant
) : ApplicationEvent() {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(application: String, clock: Clock = Companion.clock) : this(
    application,
    clock.instant()
  )
}

/**
 * Actuation at the application level has resumed.
 */
data class ApplicationActuationResumed(
  override val application: String,
  override val timestamp: Instant
) : ApplicationEvent() {
  @JsonIgnore override val ignoreRepeatedInHistory = true

  constructor(application: String, clock: Clock = Companion.clock) : this(
    application,
    clock.instant()
  )
}
