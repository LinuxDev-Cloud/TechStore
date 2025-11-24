package parcial

import akka.actor.{ActorSystem, Props}
import scala.io.StdIn

object Main extends App {

  val system = ActorSystem("SistemaPedidos")

  // Creamos el DatabaseActor
  val databaseActor = system.actorOf(Props[DatabaseActor], "databaseActor")

  // Creamos el OrderManagerActor y ReviewActor, pasándoles el databaseActor
  val orderManagerActor =
    system.actorOf(Props(new OrderManagerActor(databaseActor)), "orderManagerActor")

  val reviewActor =
    system.actorOf(Props(new ReviewActor(databaseActor)), "reviewActor")

  // Actor para consultas (muestra pedidos en consola)
  val consultaActor =
    system.actorOf(Props(new ConsultaActor(databaseActor)), "consultaActor")

  var salir = false

  while (!salir) {
    println()
    println("===== Sistema de Pedidos y Resenhas =====")
    println("1) Crear pedido")
    println("2) Crear resenha")
    println("3) Ver resenhas por producto")
    println("4) Ver todos los pedidos")
    println("5) Ver pedidos por cliente")
    println("0) Salir")
    print("Seleccione una opcion: ")

    val opcion = StdIn.readLine().trim

    opcion match {
      case "1" => crearPedidoDesdeConsola(orderManagerActor)
      case "2" => crearResenaDesdeConsola(reviewActor)
      case "3" => verResenasPorProductoDesdeConsola(reviewActor)
      case "4" => verTodosLosPedidosDesdeConsola(consultaActor)
      case "5" => verPedidosPorClienteDesdeConsola(consultaActor)
      case "0" =>
        println("Saliendo...")
        salir = true
      case _ =>
        println("Opcion no válida. Intente de nuevo.")
    }
  }

  system.terminate()

  private def leerEntero(mensaje: String): Int = {
    print(mensaje)
    StdIn.readLine().trim.toIntOption.getOrElse {
      println("Valor inválido, usando 0.")
      0
    }
  }

  private def crearPedidoDesdeConsola(orderManagerActor: akka.actor.ActorRef): Unit = {
    println("\n--- Crear Pedido ---")
    val idCliente = leerEntero("Ingrese id_cliente: ")

    var detalles = List.empty[DetallePedidoData]
    var agregarMas = true

    while (agregarMas) {
      val idProducto = leerEntero("Ingrese id_producto: ")
      val cantidad   = leerEntero("Ingrese cantidad: ")

      detalles = detalles :+ DetallePedidoData(idProducto, cantidad)

      print("¿Agregar otro producto? (s/n): ")
      val resp = StdIn.readLine().trim.toLowerCase
      if (resp != "s") agregarMas = false
    }

    val pedido = CrearPedido(idCliente, detalles)
    println("Enviando pedido al OrderManagerActor...")
    orderManagerActor ! pedido
  }

  private def crearResenaDesdeConsola(reviewActor: akka.actor.ActorRef): Unit = {
    println("\n--- Crear Resenha ---")
    val idCliente    = leerEntero("Ingrese id_cliente: ")
    val idProducto   = leerEntero("Ingrese id_producto: ")
    val calificacion = leerEntero("Ingrese calificacion (1-5): ")

    print("Ingrese comentario: ")
    val comentario = StdIn.readLine()

    val resena = CrearResena(idCliente, idProducto, calificacion, comentario)
    println("Enviando resenha al ReviewActor...")
    reviewActor ! resena
  }

  private def verResenasPorProductoDesdeConsola(reviewActor: akka.actor.ActorRef): Unit = {
    println("\n--- Ver Resenhas por Producto ---")
    val idProducto = leerEntero("Ingrese id_producto: ")

    println(s"Solicitando resenhas para el producto $idProducto...")
    reviewActor ! ObtenerResenasPorProducto(idProducto)
  }

  private def verTodosLosPedidosDesdeConsola(consultaActor: akka.actor.ActorRef): Unit = {
    println("\n--- Ver Todos los Pedidos ---")
    consultaActor ! ObtenerPedidos
  }

  private def verPedidosPorClienteDesdeConsola(consultaActor: akka.actor.ActorRef): Unit = {
    println("\n--- Ver Pedidos por Cliente ---")
    val idCliente = leerEntero("Ingrese id_cliente: ")

    consultaActor ! ObtenerPedidosPorCliente(idCliente)
  }
}