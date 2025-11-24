package parcial

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.StdIn

object WebMain extends App {

  implicit val system: ActorSystem = ActorSystem("SistemaPedidosWeb")
  implicit val ec                   = system.dispatcher
  implicit val timeout: Timeout     = Timeout(5.seconds)

  val databaseActor = system.actorOf(Props[DatabaseActor], "databaseActor-web")

  val indexHtml: String =
    """
      |<html>
      |  <head>
      |    <title>Sistema de Pedidos y Reseñas</title>
      |    <style>
      |      * {
      |        box-sizing: border-box;
      |        margin: 0;
      |        padding: 0;
      |      }
      |      body {
      |        font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      |        background: linear-gradient(135deg, #0f172a, #111827);
      |        color: #e5e7eb;
      |        min-height: 100vh;
      |        display: flex;
      |        align-items: flex-start;
      |        justify-content: center;
      |        padding: 32px 16px;
      |      }
      |      .container {
      |        width: 100%;
      |        max-width: 980px;
      |        background: rgba(15, 23, 42, 0.96);
      |        border-radius: 16px;
      |        border: 1px solid rgba(148, 163, 184, 0.4);
      |        box-shadow: 0 20px 45px rgba(0, 0, 0, 0.55);
      |        padding: 28px 32px 32px;
      |      }
      |      h1 {
      |        text-align: center;
      |        margin-bottom: 24px;
      |        font-size: 1.7rem;
      |        letter-spacing: 0.03em;
      |        color: #f9fafb;
      |      }
      |      .grid {
      |        display: grid;
      |        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      |        gap: 20px;
      |      }
      |      .card {
      |        background: radial-gradient(circle at top left, rgba(56, 189, 248, 0.14), transparent 55%),
      |                    radial-gradient(circle at bottom right, rgba(129, 140, 248, 0.16), transparent 55%),
      |                    #020617;
      |        border-radius: 14px;
      |        border: 1px solid rgba(148, 163, 184, 0.4);
      |        padding: 16px 18px 18px;
      |      }
      |      h2 {
      |        font-size: 1.05rem;
      |        margin-bottom: 10px;
      |        color: #e5e7eb;
      |      }
      |      p.description {
      |        font-size: 0.8rem;
      |        color: #9ca3af;
      |        margin-bottom: 10px;
      |      }
      |      form {
      |        margin-top: 4px;
      |        display: flex;
      |        flex-direction: column;
      |        gap: 6px;
      |      }
      |      label {
      |        font-size: 0.8rem;
      |        color: #9ca3af;
      |      }
      |      input[type="text"],
      |      input[type="number"] {
      |        width: 100%;
      |        padding: 7px 9px;
      |        margin-top: 2px;
      |        border-radius: 8px;
      |        border: 1px solid rgba(148, 163, 184, 0.65);
      |        background-color: rgba(15, 23, 42, 0.9);
      |        color: #e5e7eb;
      |        font-size: 0.85rem;
      |      }
      |      input[type="text"]::placeholder,
      |      input[type="number"]::placeholder {
      |        color: #6b7280;
      |      }
      |      input[type="submit"],
      |      a.button-link {
      |        display: inline-block;
      |        padding: 8px 16px;
      |        margin-top: 8px;
      |        background: linear-gradient(135deg, #3b82f6, #6366f1);
      |        color: #f9fafb;
      |        text-decoration: none;
      |        border-radius: 999px;
      |        border: none;
      |        cursor: pointer;
      |        font-size: 0.85rem;
      |        font-weight: 500;
      |      }
      |      input[type="submit"]:hover,
      |      a.button-link:hover {
      |        background: linear-gradient(135deg, #2563eb, #4f46e5);
      |      }
      |      .links {
      |        margin-top: 8px;
      |      }
      |      ul {
      |        margin-top: 10px;
      |        padding-left: 18px;
      |        font-size: 0.85rem;
      |      }
      |      li {
      |        margin-bottom: 4px;
      |        color: #d1d5db;
      |      }
      |      h2, a, input[type="submit"] {
      |        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
      |      }
      |    </style>
      |  </head>
      |  <body>
      |    <div class="container">
      |      <h1>Sistema de Pedidos y Reseñas</h1>
      |      <div class="grid">
      |        <section class="card">
      |          <h2>Crear pedido</h2>
      |          <p class="description">Registra un nuevo pedido para un cliente con uno o dos productos.</p>
      |          <form method="POST" action="/pedido">
      |            <label>Id Cliente:</label>
      |            <input name="idCliente" type="number" placeholder="Ej: 1"/>
      |
      |            <label>Id Producto 1:</label>
      |            <input name="idProducto1" type="number" placeholder="Ej: 1"/>
      |            <label>Cantidad Producto 1:</label>
      |            <input name="cantidad1" type="number" placeholder="Ej: 2"/>
      |
      |            <label>Id Producto 2 (opcional):</label>
      |            <input name="idProducto2" type="number" placeholder="Ej: 2"/>
      |            <label>Cantidad Producto 2:</label>
      |            <input name="cantidad2" type="number" placeholder="Ej: 1"/>
      |
      |            <input type="submit" value="Crear Pedido"/>
      |          </form>
      |        </section>
      |
      |        <section class="card">
      |          <h2>Crear reseña</h2>
      |          <p class="description">Agrega una reseña de un cliente sobre un producto.</p>
      |          <form method="POST" action="/resena">
      |            <label>Id Cliente:</label>
      |            <input name="idCliente" type="number" placeholder="Ej: 1"/>
      |
      |            <label>Id producto:</label>
      |            <input name="idProducto" type="number" placeholder="Ej: 1"/>
      |
      |            <label>Calificación (1-5):</label>
      |            <input name="calificacion" type="number" min="1" max="5" placeholder="Ej: 5"/>
      |
      |            <label>Comentario:</label>
      |            <input name="comentario" type="text" placeholder="Ej: Excelente producto"/>
      |
      |            <input type="submit" value="Crear reseña"/>
      |          </form>
      |        </section>
      |
      |        <section class="card">
      |          <h2>Ver reseñas por producto</h2>
      |          <p class="description">Consulta todas las reseñas registradas para un producto.</p>
      |          <form method="GET" action="/resenas">
      |            <label>Id producto:</label>
      |            <input name="idProducto" type="number" placeholder="Ej: 1"/>
      |            <input type="submit" value="Ver reseñas"/>
      |          </form>
      |        </section>
      |
      |        <section class="card">
      |          <h2>Ver pedidos</h2>
      |          <p class="description">Lista todos los pedidos o filtra por cliente.</p>
      |          <div class="links">
      |            <a class="button-link" href="/pedidos">Ver todos los pedidos</a>
      |          </div>
      |          <form method="GET" action="/pedidos">
      |            <label>Id cliente (opcional):</label>
      |            <input name="idCliente" type="number" placeholder="Ej: 1"/>
      |            <input type="submit" value="Ver pedidos por cliente"/>
      |          </form>
      |        </section>
      |
      |        <section class="card">
      |          <h2>Ver productos</h2>
      |          <p class="description">Consulta el catálogo de productos disponibles (id, nombre y precio).</p>
      |          <div class="links">
      |            <a class="button-link" href="/productos">Ver productos</a>
      |          </div>
      |        </section>
      |      </div>
      |    </div>
      |</html>
      |""".stripMargin

  // Usa el mismo CSS y contenedor que la página principal para resultados
  private def layoutPage(titulo: String, contenidoHtml: String): String = {
    val css =
      """
        |* {
        |  box-sizing: border-box;
        |  margin: 0;
        |  padding: 0;
        |}
        |body {
        |  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        |  background: linear-gradient(135deg, #0f172a, #111827);
        |  color: #e5e7eb;
        |  min-height: 100vh;
        |  display: flex;
        |  align-items: flex-start;
        |  justify-content: center;
        |  padding: 32px 16px;
        |}
        |.container {
        |  width: 100%;
        |  max-width: 980px;
        |  background: rgba(15, 23, 42, 0.96);
        |  border-radius: 16px;
        |  border: 1px solid rgba(148, 163, 184, 0.4);
        |  box-shadow: 0 20px 45px rgba(0, 0, 0, 0.55);
        |  padding: 28px 32px 32px;
        |}
        |h1 {
        |  text-align: center;
        |  margin-bottom: 24px;
        |  font-size: 1.7rem;
        |  letter-spacing: 0.03em;
        |  color: #f9fafb;
        |}
        |.card {
        |  background: radial-gradient(circle at top left, rgba(56, 189, 248, 0.14), transparent 55%),
        |              radial-gradient(circle at bottom right, rgba(129, 140, 248, 0.16), transparent 55%),
        |              #020617;
        |  border-radius: 14px;
        |  border: 1px solid rgba(148, 163, 184, 0.4);
        |  padding: 16px 18px 18px;
        |}
        |h2 {
        |  font-size: 1.05rem;
        |  margin-bottom: 10px;
        |  color: #e5e7eb;
        |}
        |ul {
        |  margin-top: 10px;
        |  padding-left: 18px;
        |  font-size: 0.85rem;
        |}
        |li {
        |  margin-bottom: 4px;
        |  color: #d1d5db;
        |}
        |.links {
        |  margin-top: 8px;
        |}
        |a.button-link {
        |  display: inline-block;
        |  padding: 8px 16px;
        |  margin-top: 8px;
        |  background: linear-gradient(135deg, #3b82f6, #6366f1);
        |  color: #f9fafb;
        |  text-decoration: none;
        |  border-radius: 999px;
        |  border: none;
        |  cursor: pointer;
        |  font-size: 0.85rem;
        |  font-weight: 500;
        |}
        |a.button-link:hover {
        |  background: linear-gradient(135deg, #2563eb, #4f46e5);
        |}
        |""".stripMargin

    s"""
       |<html>
       |  <head>
       |    <title>Sistema de Pedidos y Reseñas</title>
       |    <style>
       |$css
       |    </style>
       |  </head>
       |  <body>
       |    <div class="container">
       |      <h1>Sistema de Pedidos y Reseñas</h1>
       |      <div class="card" style="margin-top: 12px;">
       |        <h2>$titulo</h2>
       |        $contenidoHtml
       |      </div>
       |    </div>
       |  </body>
       |</html>
       |""".stripMargin
  }

  val route =
    pathSingleSlash {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, indexHtml))
      }
    } ~
      path("pedido") {
        post {
          formFields("idCliente", "idProducto1".?, "cantidad1".?, "idProducto2".?, "cantidad2".?) {
            (idClienteStr, idProd1Opt, cant1Opt, idProd2Opt, cant2Opt) =>
              val idCliente = idClienteStr.toIntOption.getOrElse(0)

              val detalles =
                List(
                  for {
                    idP <- idProd1Opt.flatMap(_.toIntOption)
                    c   <- cant1Opt.flatMap(_.toIntOption)
                  } yield DetallePedidoData(idP, c),
                  for {
                    idP <- idProd2Opt.flatMap(_.toIntOption)
                    c   <- cant2Opt.flatMap(_.toIntOption)
                  } yield DetallePedidoData(idP, c)
                ).flatten

              val pedido = CrearPedido(idCliente, detalles)

              val f: Future[String] = (databaseActor ? pedido).map {
                case PedidoCreado(id) => s"Pedido creado con id=$id"
                case PedidoFallido(r) => s"Error al crear pedido: $r"
                case _                => "Respuesta desconocida"
              }

              onSuccess(f) { msg =>
                val body = s"""
                   |<p>$msg</p>
                   |<div class="links" style="margin-top:12px;">
                   |  <a class="button-link" href="/">Volver al inicio</a>
                   |</div>
                   |""".stripMargin
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, layoutPage("Resultado de creación de pedido", body)))
              }
          }
        }
      } ~
      path("resena") {
        post {
          formFields("idCliente", "idProducto", "calificacion", "comentario") {
            (idClienteStr, idProductoStr, calStr, comentario) =>
              val cmd = CrearResena(
                idClienteStr.toIntOption.getOrElse(0),
                idProductoStr.toIntOption.getOrElse(0),
                calStr.toIntOption.getOrElse(0),
                comentario
              )

              val f: Future[String] = (databaseActor ? cmd).map {
                case ResenaCreada(id) => s"Reseña creada con id=$id"
                case ResenaFallida(r) => s"Error al crear reseña: $r"
                case _                => "Respuesta desconocida"
              }

              onSuccess(f) { msg =>
                val body = s"""
                   |<p>$msg</p>
                   |<div class="links" style="margin-top:12px;">
                   |  <a class="button-link" href="/">Volver al inicio</a>
                   |</div>
                   |""".stripMargin
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, layoutPage("Resultado de creación de reseña", body)))
              }
          }
        }
      } ~
      path("resenas") {
        get {
          parameter("idProducto") { idProductoStr =>
            val idProducto = idProductoStr.toIntOption.getOrElse(0)
            val f: Future[ResenasPorProducto] =
              (databaseActor ? ObtenerResenasPorProducto(idProducto)).mapTo[ResenasPorProducto]

            onSuccess(f) { res =>
              val listado = if (res.resenas.isEmpty) "<p>(sin reseñas)</p>" else {
                val filas = res.resenas.map { case (idCli, cal, com) =>
                  s"<li>Cliente $idCli - Calificación=$cal - '$com'</li>"
                }.mkString("\n")
                s"<ul>$filas</ul>"
              }
              val body = s"""
                 |$listado
                 |<div class="links" style="margin-top:12px;">
                 |  <a class="button-link" href="/">Volver al inicio</a>
                 |</div>
                 |""".stripMargin
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, layoutPage(s"Reseñas para producto $idProducto", body)))
            }
          }
        }
      } ~
      path("pedidos") {
        get {
          parameter("idCliente".?) { idClienteOpt =>
            idClienteOpt.flatMap(_.toIntOption) match {
              case Some(idCli) =>
                val f: Future[PedidosDeCliente] =
                  (databaseActor ? ObtenerPedidosPorCliente(idCli)).mapTo[PedidosDeCliente]
                onSuccess(f) { pc =>
                  val listado = if (pc.lista.isEmpty) "<p>(sin pedidos para este cliente)</p>" else {
                    val filas = pc.lista.map { case (idPed, fecha, estado) =>
                      s"<li>Pedido $idPed - Fecha=$fecha - Estado=$estado</li>"
                    }.mkString("\n")
                    s"<ul>$filas</ul>"
                  }
                  val body = s"""
                     |$listado
                     |<div class="links" style="margin-top:12px;">
                     |  <a class="button-link" href="/">Volver al inicio</a>
                     |</div>
                     |""".stripMargin
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, layoutPage(s"Pedidos del cliente $idCli", body)))
                }

              case None =>
                val f: Future[Pedidos] =
                  (databaseActor ? ObtenerPedidos).mapTo[Pedidos]
                onSuccess(f) { p =>
                  val listado = if (p.lista.isEmpty) "<p>(sin pedidos)</p>" else {
                    val filas = p.lista.map { case (idPed, idCli, nomCli, estado) =>
                      s"<li>Pedido $idPed - Cliente $idCli ($nomCli) - Estado=$estado</li>"
                    }.mkString("\n")
                    s"<ul>$filas</ul>"
                  }
                  val body = s"""
                     |$listado
                     |<div class="links" style="margin-top:12px;">
                     |  <a class="button-link" href="/">Volver al inicio</a>
                     |</div>
                     |""".stripMargin
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, layoutPage("Todos los pedidos", body)))
                }
            }
          }
        }
      } ~
      path("productos") {
        get {
          val f: Future[Productos] =
            (databaseActor ? ObtenerProductos).mapTo[Productos]

          onSuccess(f) { prods =>
            val listado = if (prods.lista.isEmpty) "<p>(sin productos)</p>" else {
              val filas = prods.lista.map { case (idProd, nombre, precio, cantidad) =>
                s"<li><strong>ID:</strong> $idProd &nbsp;|&nbsp; <strong>Nombre:</strong> $nombre &nbsp;|&nbsp; <strong>Precio:</strong> $$${precio} &nbsp;|&nbsp; <strong>Cantidad:</strong> $cantidad</li>"
              }.mkString("\n")
              s"<ul>$filas</ul>"
            }

            val body = s"""
               |$listado
               |<div class="links" style="margin-top:12px;">
               |  <a class="button-link" href="/">Volver al inicio</a>
               |</div>
               |""".stripMargin

            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, layoutPage("Catálogo de productos", body)))
          }
        }
      }

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

  println("Servidor web iniciado en http://localhost:8080. Presione ENTER para salir.")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
