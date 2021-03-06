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
package nl.knaw.dans.easy.deposit

import java.util.{ TimeZone, UUID }

import better.files.File
import better.files.File._
import nl.knaw.dans.easy.deposit.PidRequesterComponent.PidRequester
import nl.knaw.dans.easy.deposit.authentication.TokenSupport.TokenConfig
import nl.knaw.dans.easy.deposit.authentication.{ AuthConfig, AuthUser, AuthenticationProvider, TokenSupport }
import nl.knaw.dans.easy.deposit.docs.UserInfo
import org.apache.commons.configuration.PropertiesConfiguration
import org.joda.time.{ DateTime, DateTimeUtils, DateTimeZone }
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.enablers.Existence

import scala.util.{ Properties, Success, Try }

trait TestSupportFixture extends FlatSpec with Matchers with Inside with BeforeAndAfterEach with MockFactory {
  implicit def existenceOfFile[FILE <: better.files.File]: Existence[FILE] = _.exists

  lazy val testDir: File = currentWorkingDirectory / "target" / "test" / getClass.getSimpleName
  lazy val uuid: UUID = UUID.randomUUID()
  val bagDirName = "bag"

  // modification of https://github.com/DANS-KNAW/easy-split-multi-deposit/blob/ea7c2dc3d6/src/test/scala/nl.knaw.dans.easy.multideposit/actions/SetDepositPermissionsSpec.scala#L102
  lazy val (user, userGroup, unrelatedGroup) = {
    import scala.sys.process._

    // don't hardcode users and groups, since we don't know what we have on travis or personal systems
    val user = Properties.userName
    val allGroups = "cut -d: -f1 /etc/group".!!.split("\n").filterNot(_ startsWith "#").toList
    val userGroups = s"id -Gn $user".!!.split(" ").toList

    (userGroups, allGroups.diff(userGroups)) match {
      case (ug :: _, diff :: _) => (user, ug, diff)
      case (Nil, _) => throw new AssertionError("no suitable user group found")
      case (_, Nil) => throw new AssertionError("no suitable unrelated group found")
    }
  }

  val defaultUserInfo = UserInfo("user001", displayName = "fullName", email = "does.not.exist@dans.knaw.nl", lastName = "")

  def testResource(file: String): File = File(getClass.getResource(file))

  def getManualTestResource(file: String): String = {
    (testResource("/manual-test") / file).contentAsString
  }

  def clearTestDir(): Unit = {
    if (testDir.exists)
      testDir.delete().createDirectories()
  }

  val nowYMD = "2018-03-22"
  val now = s"${ nowYMD }T21:43:01.576"
  val nowUTC = s"${ nowYMD }T20:43:01Z"
  /** Causes DateTime.now() to return a predefined value. */
  DateTimeUtils.setCurrentMillisFixed(new DateTime(nowUTC).getMillis)
  DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getTimeZone("Europe/Amsterdam")))

  trait MockedPidRequester extends PidRequester with HttpContext

  def mockPidRequester: PidRequester = mock[MockedPidRequester]

  def minimalAppConfig: Configuration = {
    new Configuration("", new PropertiesConfiguration() {
      addProperty("deposits.staged", testSubDir("staged").toString())
      addProperty("deposits.drafts", testSubDir("drafts").toString())
      addProperty("deposits.submit-to", testSubDir("easy-ingest-flow-inbox").toString())
      addProperty("deposit.permissions.group", userGroup)
      addProperty("pids.generator-service", "http://pidHostDoesNotExist.dans.knaw.nl")
      addProperty("users.ldap-url", "ldap://ldapHostDoesNotExist.dans.knaw.nl")
      addProperty("users.ldap-parent-entry", "-")
      addProperty("users.ldap-admin-principal", "-")
      addProperty("users.ldap-admin-password", "-")
      addProperty("users.ldap-user-id-attr-name", "-")
      addProperty("multipart.location", (testDir / "multipart").createDirectories().toString())
      addProperty("multipart.file-size-threshold", "3145728") // 3MB
      addProperty("easy.home", "https://easy.dans.knaw.nl/ui")
    })
  }

  def createTestApp: EasyDepositApiApp = {
    new EasyDepositApiApp(minimalAppConfig) {
      override val pidRequester: PidRequester = mockPidRequester

      override def getUserInfo(user: String): Try[UserInfo] = {
        Success(defaultUserInfo)
      }
    }
  }

  private def testSubDir(drafts: String): File = {
    (testDir / drafts)
      .delete(swallowIOExceptions = true)
      .createIfNotExists(asDirectory = true, createParents = true)
  }

  private class TokenSupportImpl() extends TokenSupport with AuthConfig {

    // required by AuthConfig but not by TokenSupport
    def getAuthenticationProvider: AuthenticationProvider = mock[AuthenticationProvider]

    def getProperties: PropertiesConfiguration = new PropertiesConfiguration()
  }

  private val tokenSupport = new TokenSupportImpl()

  def jwtConfig: TokenConfig = tokenSupport.tokenConfig

  def createJWT(user: AuthUser): String = {
    tokenSupport.encodeJWT(user)
  }
}
