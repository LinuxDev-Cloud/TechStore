package parcial

import akka.actor.{Actor, ActorRef, ActorLogging}

class ReviewActor(databaseActor: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case resena @ CrearResena(idCliente, idProducto, calificacion, comentario) =>
      manejarCrearResena(resena)

    case ObtenerResenasPorProducto(idProducto) =>
      log.info(s"ReviewActor: solicitando resenhas para producto $idProducto al DatabaseActor")
      databaseActor ! ObtenerResenasPorProducto(idProducto)

    case ResenaCreada(idResena) =>
      log.info(s"ReviewActor: resenha creada con id=$idResena")
      println(s"Resenha creada correctamente con id=$idResena")

    case ResenaFallida(razon) =>
      log.warning(s"ReviewActor: error al crear/consultar resenha: $razon")
      println(s"Error al procesar resenha: $razon")

    case ResenasPorProducto(idProducto, resenas) =>
      log.info(s"ReviewActor: recibidas ${resenas.size} resenhas para producto $idProducto")
      println(s"Resenhas para producto $idProducto:")
      if (resenas.isEmpty) {
        println("(sin resenhas)")
      } else {
        resenas.foreach { case (idCliente, calificacion, comentario) =>
          println(s"- Cliente $idCliente: calificación=$calificacion, comentario='$comentario'")
        }
      }
  }

  private def manejarCrearResena(resena: CrearResena): Unit = {
    if (resena.calificacion < 1 || resena.calificacion > 5) {
      println("Error al procesar resenha: Calificación inválida (debe estar entre 1 y 5)")
    } else if (resena.comentario.trim.isEmpty) {
      println("Error al procesar resenha: El comentario de la resenha no puede estar vacío")
    } else {
      log.info(s"ReviewActor: reenviando resenha válida al DatabaseActor")
      databaseActor ! resena
    }
  }
}