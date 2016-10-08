package net.petitviolet.ex.akka.stream

import akka.actor._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.{Done, NotUsed}

import scala.concurrent.Future
import scala.io.StdIn
import scala.language.postfixOps


private object AkkaStreamStandard extends App {
  case class Message(value: String) extends AnyVal

  import akka.stream.scaladsl._
  implicit val system = ActorSystem("akka-stream-prac")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  // source with actor
  val source: Source[Message, SourceQueueWithComplete[Message]] =
    Source.queue[Message](100, OverflowStrategy.backpressure)

  // flow
  val flow: Flow[Message, Message, NotUsed] =
    Flow[Message].map { r => r.copy(value = s"(Mapped: ${r.value})") }

  // another flow
  val accumulater: Flow[Message, String, NotUsed] =
    Flow[Message].fold("init") { (acc, rep) => s"$acc :: ${rep.value}" }

  // sink just printing message
  val sink: Sink[String, Future[Done]] = Sink.foreach[String] { println }

  // simple graph
  val graph: RunnableGraph[SourceQueueWithComplete[Message]] =
    source via flow via accumulater to sink

  // queue for publisher of graph
  val queue: SourceQueueWithComplete[Message] = graph.run()

  // wait preparing graph
  //  Thread.sleep(100L)

  queue offer Message("hello!")
  queue offer Message("100")
  queue offer Message("good")
  queue complete

  println("push Enter to shutdown process.")
  StdIn.readLine()
  system.terminate()
}
