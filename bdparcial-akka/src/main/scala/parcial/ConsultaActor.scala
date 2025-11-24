package parcial

import akka.actor.{Actor, ActorLogging, ActorRef}

class ConsultaActor(databaseActor: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case ObtenerPedidos =>
      log.info("ConsultaActor: solicitando todos los pedidos al DatabaseActor")
      databaseActor ! ObtenerPedidos

    case ObtenerPedidosPorCliente(idCliente) =>
      log.info(s"ConsultaActor: solicitando pedidos del cliente $idCliente al DatabaseActor")
      databaseActor ! ObtenerPedidosPorCliente(idCliente)

    case Pedidos(lista) =>
      println("\n────────────────────────────────────")
      println("  LISTA DE TODOS LOS PEDIDOS")
      println("────────────────────────────────────")
      if (lista.isEmpty) {
        println("(sin pedidos)")
      } else {
        lista.foreach { case (idPedido, idCliente, nombreCliente, estado) =>
          println(s"• Pedido $idPedido  |  Cliente $idCliente ($nombreCliente)  |  Estado = $estado")
        }
      }

    case PedidosDeCliente(idCliente, lista) =>
      println("\n────────────────────────────────────")
      println(s"  PEDIDOS DEL CLIENTE $idCliente")
      println("────────────────────────────────────")
      if (lista.isEmpty) {
        println("(sin pedidos para este cliente)")
      } else {
        lista.foreach { case (idPedido, fechaCreacion, estado) =>
          println(s"• Pedido $idPedido  |  Fecha = $fechaCreacion  |  Estado = $estado")
        }
      }
  }
}
