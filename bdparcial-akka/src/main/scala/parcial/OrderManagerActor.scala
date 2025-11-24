package parcial

import akka.actor.{Actor, ActorRef, ActorLogging}

class OrderManagerActor(databaseActor: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case pedido @ CrearPedido(idCliente, detalles) =>
      manejarCrearPedido(pedido)

    case PedidoCreado(idPedido) =>
      log.info(s"OrderManagerActor: pedido creado con id=$idPedido")
      println(s"Pedido creado correctamente con id=$idPedido")

    case PedidoFallido(razon) =>
      log.warning(s"OrderManagerActor: error al crear pedido: $razon")
      println(s"Error al crear pedido: $razon")
  }

  private def manejarCrearPedido(pedido: CrearPedido): Unit = {
    if (pedido.detalles.isEmpty || pedido.detalles.exists(_.cantidad <= 0)) {
      println("Error al crear pedido: Pedido inválido: sin detalles o cantidades no válidas")
    } else {
      log.info(s"OrderManagerActor: enviando pedido al DatabaseActor")
      databaseActor ! pedido
    }
  }
}