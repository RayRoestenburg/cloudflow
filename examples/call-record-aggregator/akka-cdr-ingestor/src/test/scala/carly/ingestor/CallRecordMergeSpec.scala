/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
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

package carly.ingestor

import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.testkit._
import org.scalatest._
import org.scalatest.concurrent._

import cloudflow.akkastream.testkit.scaladsl._
import carly.data._

class CallRecordMergeSpec extends WordSpec with MustMatchers with ScalaFutures with BeforeAndAfterAll {

  private implicit val system = ActorSystem("CallRecordMergeSpec")
  private implicit val mat = ActorMaterializer()

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A CallRecordMerge" should {
    "merge incoming data" in {
      val testkit = AkkaStreamletTestKit(system, mat)
      val streamlet = new CallRecordMerge

      val instant = Instant.now.toEpochMilli / 1000
      val past = Instant.now.minus(5000, ChronoUnit.DAYS).toEpochMilli / 1000

      val cr1 = CallRecord("user-1", "user-2", "f", 10L, instant)
      val cr2 = CallRecord("user-1", "user-2", "f", 15L, instant)
      val cr3 = CallRecord("user-1", "user-2", "f", 18L, instant)
      val cr4 = CallRecord("user-1", "user-2", "f", 40L, past)
      val cr5 = CallRecord("user-1", "user-2", "f", 70L, past)
      val cr6 = CallRecord("user-3", "user-1", "f", 80L, past)

      val source0 = Source(Vector(cr1, cr2, cr3))
      val source1 = Source(Vector(cr4, cr5))
      val source2 = Source(Vector(cr6))

      val in0 = testkit.inletFromSource(streamlet.in0, source0)
      val in1 = testkit.inletFromSource(streamlet.in1, source1)
      val in2 = testkit.inletFromSource(streamlet.in2, source2)
      val out = testkit.outletAsTap(streamlet.out)

      testkit.run(streamlet, List(in0, in1, in2), out, () ⇒ {
        out.probe.expectMsg(("user-1", cr1))
        out.probe.expectMsg(("user-1", cr4))
        out.probe.expectMsg(("user-3", cr6))
        out.probe.expectMsg(("user-1", cr2))
        out.probe.expectMsg(("user-1", cr5))
        out.probe.expectMsg(("user-1", cr3))
      })

      out.probe.expectMsg(Completed)
    }
  }
}

