package net.petitviolet.ex.akka.stream

import akka.NotUsed
import akka.actor._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.io.StdIn

private object AkkaStreamPracWithActorRef extends App {
  sealed trait Message extends Any { def value: String }
  case class Letter(value: String) extends Message
  case object Finish extends Message { def value: String = "finish" }

  import akka.stream.scaladsl._

  implicit val system = ActorSystem("akka-stream-prac")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

//  class PublishActor extends Actor {
//    // publish [[Message]] or OnComplete
//    override def receive: Actor.Receive = {
//      case m@Letter(msg) =>
//        println(s"sender: $sender")
//        sender ! m.copy(value = s"publish: $msg")
//      case Finish =>
//        println(s"sender: $sender")
//        sender ! Finish
//    }
//  }

  class FlowActor extends Actor {
    // subscribe and publish
    override def receive: Actor.Receive = {
      case Letter(msg) =>
        sender ! Letter(s"(Mapped: $msg)")
      case any =>
        sender ! any
    }
  }

  class SubscribeActor extends Actor {
    // just subscribe
    override def receive: Actor.Receive = {
      case Finish => println(s"finish process!")
      case any => println(s"subscribed: $any")
    }
  }

  // source with actorRef
  val source: Source[Message, ActorRef] = Source.actorRef[Message](65536, OverflowStrategy.fail)

  // flow with actor
  val flow: Flow[Message, Message, NotUsed] = {
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 1.second
    val flowActor = system.actorOf(Props[FlowActor])
    def flowWithActor(reply: Message): Future[Message] = (flowActor ? reply).mapTo[Message]

    Flow[Message].mapAsync[Message](3)(flowWithActor)
  }

  // another flow without actor
  val accumulator: Flow[Message, String, NotUsed] =
    Flow[Message].fold("init") { (acc, rep) => s"$acc :: ${rep.value}" }

  // subscriber
  val subscribeActor = system.actorOf(Props[SubscribeActor])
  // sink with actorRef
  val sink: Sink[Any, NotUsed] = Sink.actorRef(subscribeActor, onCompleteMessage = Finish)

  // simple graph
  val graph: RunnableGraph[ActorRef] = source via flow via accumulator to sink

  val sourceActorRef: ActorRef = graph.run

  // wait preparing graph
  Thread.sleep(100L)

  sourceActorRef ! Letter("hello!")
  sourceActorRef ! Letter("100")
  sourceActorRef ! Letter("good")

  // force complete upstream source
  sourceActorRef ! PoisonPill

  println("push Enter to shutdown process.")

  StdIn.readLine()

  system.terminate()
}

