/**
 * Copyright (C) 2018 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.deposit.docs

import nl.knaw.dans.easy.deposit.docs.JsonUtil.RichJsonInput
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.JsonInput

import scala.util.Try

case class UserInfo(userName: String,
                    firstName: Option[String] = None,
                    prefix: Option[String] = None,
                    lastName: String,
                    displayName: String,
                    email: String,
                   )
object UserInfo extends DebugEnhancedLogging {
  def apply(input: JsonInput): Try[UserInfo] = input.deserialize[UserInfo]

  def apply(attributes: Map[String, Seq[String]]): UserInfo = {

    def getOption(key: String): Option[String] = {
      attributes
        .getOrElse(key, Seq.empty)
        .headOption
    }

    def getAttribute(key: String): String = {
      getOption(key).getOrElse {
        logger.warn(s"user has no attribute '$key' $attributes")
        ""
      }
    }

    // For possible attribute keys see
    // https://github.com/DANS-KNAW/easy-app/blob/master/lib-deprecated/dans-ldap/src/main/java/nl/knaw/dans/common/ldap/management/
    //   DANSSchema.java
    //   EasySchema.java
    // https://github.com/DANS-KNAW/dans.easy-test-users/blob/master/templates
    new UserInfo(

      // mandatory: https://github.com/DANS-KNAW/dans.easy-ldap-dir/blob/f17c391/files/easy-schema.ldif#L83-L84
      userName = getAttribute("uid"),

      firstName = getOption("cn"),
      prefix = getOption("dansPrefixes"),
      lastName = getAttribute("sn"),
      displayName = getAttribute("displayName"),
      email = getAttribute("mail"),
    )
  }
}
