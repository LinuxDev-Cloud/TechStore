package parcial

import akka.actor.{Actor, ActorLogging}
import java.sql.{Connection, PreparedStatement, ResultSet}

class FacturacionActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case ObtenerFacturasPorCliente =>
      manejarObtenerFacturasPorCliente()
    case ObtenerFacturaCliente(idCliente) =>
      manejarObtenerFacturaCliente(idCliente)
  }

  private def manejarObtenerFacturasPorCliente(): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmtClientes: PreparedStatement = null
    var stmtPedidos: PreparedStatement = null
    var stmtDetalles: PreparedStatement = null
    var rsClientes: ResultSet = null
    var rsPedidos: ResultSet = null
    var rsDetalles: ResultSet = null

    try {
      conexion = DB.obtenerConexion()

      // Obtener todos los clientes que tienen pedidos
      val sqlClientes = """
        |SELECT DISTINCT c.id_cliente, c.nombre
        |FROM clientes c
        |INNER JOIN pedidos p ON c.id_cliente = p.id_cliente
        |ORDER BY c.id_cliente
        |""".stripMargin

      stmtClientes = conexion.prepareStatement(sqlClientes)
      rsClientes = stmtClientes.executeQuery()

      var facturas = List.empty[FacturaCliente]

      while (rsClientes.next()) {
        val idCliente = rsClientes.getInt("id_cliente")
        val nombreCliente = rsClientes.getString("nombre")

        // Obtener pedidos del cliente
        val sqlPedidos = """
          |SELECT id_pedido, fecha_creacion
          |FROM pedidos
          |WHERE id_cliente = ?
          |ORDER BY fecha_creacion
          |""".stripMargin

        stmtPedidos = conexion.prepareStatement(sqlPedidos)
        stmtPedidos.setInt(1, idCliente)
        rsPedidos = stmtPedidos.executeQuery()

        var pedidosConTotal = List.empty[(Int, String, BigDecimal)]
        var totalGeneral = BigDecimal(0)

        while (rsPedidos.next()) {
          val idPedido = rsPedidos.getInt("id_pedido")
          val fechaCreacion = rsPedidos.getString("fecha_creacion")

          // Calcular total del pedido
          val sqlDetalles = """
            |SELECT SUM(d.cantidad * d.precio_unitario) as total_pedido
            |FROM detalles_pedido d
            |WHERE d.id_pedido = ?
            |""".stripMargin

          stmtDetalles = conexion.prepareStatement(sqlDetalles)
          stmtDetalles.setInt(1, idPedido)
          rsDetalles = stmtDetalles.executeQuery()

          val totalPedido = if (rsDetalles.next()) {
            BigDecimal(rsDetalles.getBigDecimal("total_pedido"))
          } else {
            BigDecimal(0)
          }

          rsDetalles.close()
          stmtDetalles.close()

          pedidosConTotal = pedidosConTotal :+ (idPedido, fechaCreacion, totalPedido)
          totalGeneral = totalGeneral + totalPedido
        }

        stmtPedidos.close()
        rsPedidos.close()

        facturas = facturas :+ FacturaCliente(idCliente, nombreCliente, pedidosConTotal, totalGeneral)
      }

      log.info(s"Generadas ${facturas.size} facturas de clientes")
      remitente ! facturas

    } catch {
      case ex: Exception =>
        log.error(ex, "Error al obtener facturas de clientes")
        remitente ! FacturaFallida(ex.getMessage)

    } finally {
      if (rsClientes != null) try rsClientes.close() catch { case _: Exception => () }
      if (rsPedidos != null) try rsPedidos.close() catch { case _: Exception => () }
      if (rsDetalles != null) try rsDetalles.close() catch { case _: Exception => () }
      if (stmtClientes != null) try stmtClientes.close() catch { case _: Exception => () }
      if (stmtPedidos != null) try stmtPedidos.close() catch { case _: Exception => () }
      if (stmtDetalles != null) try stmtDetalles.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }

  private def manejarObtenerFacturaCliente(idCliente: Int): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmtCliente: PreparedStatement = null
    var stmtPedidos: PreparedStatement = null
    var stmtDetalles: PreparedStatement = null
    var rsCliente: ResultSet = null
    var rsPedidos: ResultSet = null
    var rsDetalles: ResultSet = null

    try {
      conexion = DB.obtenerConexion()

      // Obtener datos del cliente
      val sqlCliente = "SELECT id_cliente, nombre FROM clientes WHERE id_cliente = ?"
      stmtCliente = conexion.prepareStatement(sqlCliente)
      stmtCliente.setInt(1, idCliente)
      rsCliente = stmtCliente.executeQuery()

      if (!rsCliente.next()) {
        remitente ! FacturaFallida(s"Cliente con id $idCliente no encontrado")
        return
      }

      val nombreCliente = rsCliente.getString("nombre")

      // Obtener pedidos del cliente
      val sqlPedidos = """
        |SELECT id_pedido, fecha_creacion
        |FROM pedidos
        |WHERE id_cliente = ?
        |ORDER BY fecha_creacion
        |""".stripMargin

      stmtPedidos = conexion.prepareStatement(sqlPedidos)
      stmtPedidos.setInt(1, idCliente)
      rsPedidos = stmtPedidos.executeQuery()

      var pedidosConTotal = List.empty[(Int, String, BigDecimal)]
      var totalGeneral = BigDecimal(0)

      while (rsPedidos.next()) {
        val idPedido = rsPedidos.getInt("id_pedido")
        val fechaCreacion = rsPedidos.getString("fecha_creacion")

        // Calcular total del pedido
        val sqlDetalles = """
          |SELECT SUM(d.cantidad * d.precio_unitario) as total_pedido
          |FROM detalles_pedido d
          |WHERE d.id_pedido = ?
          |""".stripMargin

        stmtDetalles = conexion.prepareStatement(sqlDetalles)
        stmtDetalles.setInt(1, idPedido)
        rsDetalles = stmtDetalles.executeQuery()

        val totalPedido = if (rsDetalles.next()) {
          BigDecimal(rsDetalles.getBigDecimal("total_pedido"))
        } else {
          BigDecimal(0)
        }

        rsDetalles.close()
        stmtDetalles.close()

        pedidosConTotal = pedidosConTotal :+ (idPedido, fechaCreacion, totalPedido)
        totalGeneral = totalGeneral + totalPedido
      }

      val factura = FacturaCliente(idCliente, nombreCliente, pedidosConTotal, totalGeneral)
      log.info(s"Generada factura para cliente $idCliente: $$${totalGeneral}")
      remitente ! factura

    } catch {
      case ex: Exception =>
        log.error(ex, s"Error al obtener factura del cliente $idCliente")
        remitente ! FacturaFallida(ex.getMessage)

    } finally {
      if (rsCliente != null) try rsCliente.close() catch { case _: Exception => () }
      if (rsPedidos != null) try rsPedidos.close() catch { case _: Exception => () }
      if (rsDetalles != null) try rsDetalles.close() catch { case _: Exception => () }
      if (stmtCliente != null) try stmtCliente.close() catch { case _: Exception => () }
      if (stmtPedidos != null) try stmtPedidos.close() catch { case _: Exception => () }
      if (stmtDetalles != null) try stmtDetalles.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }
}
