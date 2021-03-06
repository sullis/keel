/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.api.ResourceArtifactSummary
import com.netflix.spinnaker.keel.core.api.ResourceSummary
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationPaused
import com.netflix.spinnaker.keel.events.ResourceActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DIFF
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ERROR
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.PAUSED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.RESUMED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNKNOWN
import com.netflix.spinnaker.keel.persistence.ResourceStatus.VETOED
import java.time.Duration
import java.time.Instant

data class ResourceHeader(
  val id: String,
  val kind: ResourceKind
) {
  constructor(resource: Resource<*>) : this(
    resource.id,
    resource.kind
  )
}

interface ResourceRepository : PeriodicallyCheckedRepository<Resource<out ResourceSpec>> {
  /**
   * Invokes [callback] once with each registered resource.
   */
  fun allResources(callback: (ResourceHeader) -> Unit)

  /**
   * Retrieves a single resource by its unique [id].
   *
   * @return The resource represented by [id] or `null` if [id] is unknown.
   * @throws NoSuchResourceException if [id] does not map to a resource in the repository.
   */
  fun get(id: String): Resource<out ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  /**
   * Fetches resources for a given application.
   */
  fun getResourceIdsByApplication(application: String): List<String>

  /**
   * Fetches resources for a given application.
   */
  fun getResourcesByApplication(application: String): List<Resource<*>>

  /**
   * Fetches resource summary, including the status
   */
  fun getResourceSummaries(deliveryConfig: DeliveryConfig): List<ResourceSummary>

  /**
   * Persists a resource.
   *
   * @return the `uid` of the stored resource.
   */
  fun store(resource: Resource<*>)

  /**
   * Deletes the resource represented by [id].
   */
  fun delete(id: String)

  /**
   * Retrieves the history of persisted events for [application].
   *
   * @param application the name of the application.
   * @param limit the maximum number of events to return.
   */
  fun applicationEventHistory(application: String, limit: Int = DEFAULT_MAX_EVENTS): List<ApplicationEvent>

  /**
   * Retrieves the history of persisted events for [application].
   *
   * @param application the name of the application.
   * @param until the time of the oldest event to return.
   */
  fun applicationEventHistory(application: String, until: Instant): List<ApplicationEvent>

  /**
   * Retrieves the history of state change events for the resource represented by [uid].
   *
   * @param id the resource id.
   * @param limit the maximum number of events to return.
   */
  fun eventHistory(id: String, limit: Int = DEFAULT_MAX_EVENTS): List<ResourceEvent>

  /**
   * Retrieves the last event from the history of state change events for the resource represented by [id] or null if
   * none found.
   *
   * @param id the resource id.
   */
  fun lastEvent(id: String): ResourceEvent? = eventHistory(id, 1).firstOrNull()

  /**
   * Records an event associated with a resource.
   */
  fun appendHistory(event: ResourceEvent)

  /**
   * Records an event associated with an application.
   * TODO: adding this here as there's no ApplicationRepository or EventRepository, but might want to move it.
   */
  fun appendHistory(event: ApplicationEvent)

  /**
   * Returns between zero and [limit] resources that have not been checked (i.e. returned by this
   * method) in at least [minTimeSinceLastCheck].
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<out ResourceSpec>>

  fun getStatus(id: String): ResourceStatus {
    val history = eventHistory(id, 10)
    return when {
      history.isHappy() -> HAPPY
      history.isUnhappy() -> UNHAPPY
      history.isDiff() -> DIFF
      history.isActuating() -> ACTUATING
      history.isError() -> ERROR
      history.isCreated() -> CREATED
      history.isPaused() -> PAUSED
      history.isVetoed() -> VETOED
      history.isResumed() -> RESUMED
      else -> UNKNOWN
    }
  }

  private fun List<ResourceEvent>.isHappy(): Boolean {
    return first() is ResourceValid || first() is ResourceDeltaResolved
  }

  private fun List<ResourceEvent>.isActuating(): Boolean {
    return first() is ResourceActuationLaunched || first() is ResourceTaskSucceeded ||
      // we might want to move ResourceTaskFailed to isError later on
      first() is ResourceTaskFailed
  }

  private fun List<ResourceEvent>.isError(): Boolean {
    return first() is ResourceCheckError
  }

  private fun List<ResourceEvent>.isCreated(): Boolean {
    return first() is ResourceCreated
  }

  private fun List<ResourceEvent>.isDiff(): Boolean {
    return first() is ResourceDeltaDetected || first() is ResourceMissing || first() is ResourceCheckUnresolvable
  }

  private fun List<ResourceEvent>.isPaused(): Boolean {
    return first() is ResourceActuationPaused
  }

  private fun List<ResourceEvent>.isVetoed(): Boolean {
    return first() is ResourceActuationVetoed
  }

  private fun List<ResourceEvent>.isResumed(): Boolean {
    return first() is ResourceActuationResumed
  }

  /**
   * Checks last 10 events for flapping between only ResourceActuationLaunched and ResourceDeltaDetected
   */
  private fun List<ResourceEvent>.isUnhappy(): Boolean {
    val recentSliceOfHistory = this.subList(0, Math.min(10, this.size))
    val filteredHistory = recentSliceOfHistory.filter { it is ResourceDeltaDetected || it is ResourceActuationLaunched }
    if (filteredHistory.size != recentSliceOfHistory.size) {
      // there are other events, we're not thrashing.
      return false
    }
    return true
  }

  fun Resource<*>.toResourceSummary(deliveryConfig: DeliveryConfig) =
    ResourceSummary(
      resource = this,
      status = getStatus(id), // todo eb: this will become expensive
      moniker = if (spec is Monikered) {
        (spec as Monikered).moniker
      } else {
        null
      },
      locations = if (spec is Locatable<*>) {
        SimpleLocations(
          account = (spec as Locatable<*>).locations.account,
          vpc = (spec as Locatable<*>).locations.vpc,
          regions = (spec as Locatable<*>).locations.regions.map { SimpleRegionSpec(it.name) }.toSet()
        )
      } else {
        null
      },
      artifact = deliveryConfig.let {
        findAssociatedArtifact(it)?.toResourceArtifactSummary()
      }
    )

  private fun DeliveryArtifact.toResourceArtifactSummary() = ResourceArtifactSummary(name, type, reference)

  companion object {
    const val DEFAULT_MAX_EVENTS: Int = 10
  }
}

@Suppress("UNCHECKED_CAST", "EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : ResourceSpec> ResourceRepository.get(id: String): Resource<T> =
  get(id).also { check(it.spec is T) } as Resource<T>

abstract class NoSuchResourceException(override val message: String?) :
  NoSuchEntityException(message)

class NoSuchResourceId(id: String) :
  NoSuchResourceException("No resource with id $id exists in the database")

class NoSuchApplication(application: String) :
  NoSuchEntityException("No resource with application name $application exists in the database")

enum class ResourceStatus {
  HAPPY, ACTUATING, UNHAPPY, CREATED, DIFF, ERROR, PAUSED, VETOED, RESUMED, UNKNOWN
}
