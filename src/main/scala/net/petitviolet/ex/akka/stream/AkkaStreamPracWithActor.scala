package net.petitviolet.ex.akka.stream

import akka.NotUsed
import akka.actor._
import akka.pattern.ask
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{ActorPublisher, ActorSubscriber, OneByOneRequestStrategy, RequestStrategy}
import akka.stream.{OverflowStrategy, ActorMaterializer, ClosedShape}
import akka.util.Timeout
import org.reactivestreams.Publisher

import scala.concurrent.Future
import scala.io.StdIn

private object AkkaStreamPracWithActor extends App {
  case class Message(value: String) extends AnyVal
  case object Finish

  import akka.stream.scaladsl._

  implicit val system = ActorSystem("akka-stream-prac")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  class PublishActor extends ActorPublisher[Message] {
    // publish [[Message]] or OnComplete
    override def receive: Actor.Receive = {
      case s: String =>
        onNext(Message(s"Nice: $s"))
      case i: Int =>
        onNext(Message(s"Great: ${i * 100}"))
      case Finish =>
        onComplete()
    }
  }

  class FlowActor extends Actor {
    // subscribe and publish
    override def receive: Actor.Receive = {
      case Message(msg) => sender() ! Message(s"(Mapped: $msg)")
      case any         => println(s"??? => $any")
    }
  }

  class SubscribeActor extends ActorSubscriber {
    override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

    // just subscribe
    override def receive: Actor.Receive = {
      case OnNext(any) => println(s"subscribed: $any")
      case OnComplete  => println(s"finish process!")
    }
  }

  // publisher actor
  val publishActorRef = system.actorOf(Props[PublishActor])

  // source with actor
  val source: Source[Message, NotUsed] = {
    val publisher: Publisher[Message] = ActorPublisher(publishActorRef)
    Source.fromPublisher(publisher)
  }

  // flow
  val flow: Flow[Message, Message, NotUsed] = {
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 1.second
    val flowActor = system.actorOf(Props[FlowActor])
    def flowWithActor(reply: Message): Future[Message] = (flowActor ? reply).mapTo[Message]

    Flow[Message].mapAsync[Message](3)(flowWithActor)
  }

  // simple implementation without actor
  val _flow: Flow[Message, Message, NotUsed] = Flow[Message].map { r => r.copy(value = s"(Mapped: ${r.value})") }

  // another flow without actor
  val accumulator: Flow[Message, String, NotUsed] =
    Flow[Message].fold("init") { (acc, rep) => s"$acc :: ${rep.value}" }

  // sink with actor
  val sink: Sink[String, NotUsed] = {
    val printActor = system.actorOf(Props[SubscribeActor])
    Sink.fromSubscriber[String](ActorSubscriber[String](printActor))
  }

  // simple graph
  val graph: RunnableGraph[NotUsed] =
    source via flow via accumulator to sink

  // written by DSL
  val _graph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      source ~> flow ~> accumulator ~> sink
      ClosedShape
    }
  }

  graph.run

  // wait preparing graph
  Thread.sleep(100L)

  publishActorRef ! "hello!"

  publishActorRef ! 100

  publishActorRef ! "good"

  publishActorRef ! Finish

  println("push Enter to shutdown process.")

  StdIn.readLine()

  system.terminate()
}

