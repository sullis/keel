package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.docs.Description
import com.netflix.spinnaker.keel.serialization.SubmittedEnvironmentDeserializer

const val DEFAULT_SERVICE_ACCOUNT = "keel@spinnaker.io"

@Description("A manifest specifying the environments and resources that comprise an application.")
data class SubmittedDeliveryConfig(
  val name: String,
  val application: String,
  @Description("The service account Spinnaker will authenticate with when making changes.")
  val serviceAccount: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<SubmittedEnvironment> = emptySet()
)

@JsonDeserialize(using = SubmittedEnvironmentDeserializer::class)
data class SubmittedEnvironment(
  val name: String,
  val resources: Set<SubmittedResource<*>>,
  val constraints: Set<Constraint> = emptySet(),
  val notifications: Set<NotificationConfig> = emptySet(),
  @Description("Optional locations that are propagated to any [resources] where they are not specified.")
  val locations: SubnetAwareLocations? = null
)
