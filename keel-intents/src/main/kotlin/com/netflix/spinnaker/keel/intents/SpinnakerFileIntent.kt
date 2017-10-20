/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.intents

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec

private const val KIND = "SpinnakerFile"
private const val CURRENT_SCHEMA = "-1"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = "schema")
class SpinnakerFileIntent
@JsonCreator constructor(spec: SpinnakerFileSpec): Intent<SpinnakerFileSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {

  override fun getId() = "$KIND:TODO"
}

data class SpinnakerFileSpec(
  // TODO rz
  val todo: String
) : IntentSpec