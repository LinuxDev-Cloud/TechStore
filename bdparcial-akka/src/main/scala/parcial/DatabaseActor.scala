package parcial

import akka.actor.{Actor, ActorLogging}
import java.sql.{Connection, PreparedStatement, ResultSet, Statement}

class DatabaseActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case CrearPedido(idCliente, detalles) =>
      manejarCrearPedido(idCliente, detalles)

    case CrearResena(idCliente, idProducto, calificacion, comentario) =>
      manejarCrearResena(idCliente, idProducto, calificacion, comentario)

    case ObtenerResenasPorProducto(idProducto) =>
      manejarObtenerResenasPorProducto(idProducto)

    case ObtenerPedidos =>
      manejarObtenerPedidos()

    case ObtenerPedidosPorCliente(idCliente) =>
      manejarObtenerPedidosPorCliente(idCliente)

    case ObtenerProductos =>
      manejarObtenerProductos()
  }

  private def manejarCrearPedido(idCliente: Int, detalles: List[DetallePedidoData]): Unit = {
    val remitente = sender()

    if (detalles.isEmpty) {
      remitente ! PedidoFallido("El pedido no tiene detalles")
      return
    }

    var conexion: Connection = null
    var stmtPedido: PreparedStatement = null
    var stmtDetalle: PreparedStatement = null
    var stmtStock: PreparedStatement = null
    var stmtUpdateStock: PreparedStatement = null
    var rsStock: ResultSet = null

    try {
      conexion = DB.obtenerConexion()
      conexion.setAutoCommit(false)

      // Validar stock suficiente para todos los productos
      for (d <- detalles) {
        val sqlStock = "SELECT cantidad FROM productos WHERE id_producto = ? FOR UPDATE"
        stmtStock = conexion.prepareStatement(sqlStock)
        stmtStock.setInt(1, d.idProducto)
        rsStock = stmtStock.executeQuery()
        if (!rsStock.next()) {
          throw new RuntimeException(s"Producto con id ${d.idProducto} no existe")
        }
        val stockActual = rsStock.getInt("cantidad")
        if (stockActual < d.cantidad) {
          throw new RuntimeException(s"Stock insuficiente para producto ${d.idProducto}")
        }
        stmtStock.close()
        rsStock.close()
      }

      val sqlPedido =
        "INSERT INTO pedidos (id_cliente, fecha_creacion, estado) VALUES (?, NOW(), 'PENDIENTE')"
      stmtPedido = conexion.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)
      stmtPedido.setInt(1, idCliente)
      stmtPedido.executeUpdate()

      val rs: ResultSet = stmtPedido.getGeneratedKeys
      if (!rs.next()) {
        throw new RuntimeException("No se pudo obtener el id_pedido generado")
      }
      val idPedidoGenerado = rs.getInt(1)

      val sqlDetalle =
        "INSERT INTO detalles_pedido (id_pedido, id_producto, cantidad, precio_unitario) " +
          "VALUES (?, ?, ?, (SELECT precio FROM productos WHERE id_producto = ?))"
      stmtDetalle = conexion.prepareStatement(sqlDetalle)

      detalles.foreach { d =>
        stmtDetalle.setInt(1, idPedidoGenerado)
        stmtDetalle.setInt(2, d.idProducto)
        stmtDetalle.setInt(3, d.cantidad)
        stmtDetalle.setInt(4, d.idProducto)
        stmtDetalle.addBatch()
      }

      stmtDetalle.executeBatch()

      // Descontar stock
      val sqlUpdateStock = "UPDATE productos SET cantidad = cantidad - ? WHERE id_producto = ?"
      stmtUpdateStock = conexion.prepareStatement(sqlUpdateStock)
      detalles.foreach { d =>
        stmtUpdateStock.setInt(1, d.cantidad)
        stmtUpdateStock.setInt(2, d.idProducto)
        stmtUpdateStock.addBatch()
      }
      stmtUpdateStock.executeBatch()

      val sqlActualizar = "UPDATE pedidos SET estado = 'CONFIRMADO' WHERE id_pedido = ?"
      val stmtActualizar = conexion.prepareStatement(sqlActualizar)
      try {
        stmtActualizar.setInt(1, idPedidoGenerado)
        stmtActualizar.executeUpdate()
      } finally {
        stmtActualizar.close()
      }

      conexion.commit()

      log.info(s"Pedido creado en BD con id_pedido=$idPedidoGenerado")
      remitente ! PedidoCreado(idPedidoGenerado)

    } catch {
      case ex: Exception =>
        if (conexion != null) {
          try conexion.rollback() catch { case _: Exception => () }
        }
        log.error(ex, "Error al crear pedido en la base de datos")
        remitente ! PedidoFallido(ex.getMessage)

    } finally {
      if (stmtDetalle != null) try stmtDetalle.close() catch { case _: Exception => () }
      if (stmtPedido != null) try stmtPedido.close() catch { case _: Exception => () }
      if (stmtStock != null) try stmtStock.close() catch { case _: Exception => () }
      if (stmtUpdateStock != null) try stmtUpdateStock.close() catch { case _: Exception => () }
      if (rsStock != null) try rsStock.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }

  private def manejarCrearResena(
      idCliente: Int,
      idProducto: Int,
      calificacion: Int,
      comentario: String
  ): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmtValidacion: PreparedStatement = null
    var stmtInsert: PreparedStatement = null
    var rsValidacion: ResultSet = null

    try {
      conexion = DB.obtenerConexion()
      // Validar que el cliente haya comprado previamente este producto
      val sqlValidacion =
        """
          |SELECT COUNT(*) AS total
          |FROM pedidos p
          |JOIN detalles_pedido d ON p.id_pedido = d.id_pedido
          |WHERE p.id_cliente = ? AND d.id_producto = ?
          |""".stripMargin

      stmtValidacion = conexion.prepareStatement(sqlValidacion)
      stmtValidacion.setInt(1, idCliente)
      stmtValidacion.setInt(2, idProducto)
      rsValidacion = stmtValidacion.executeQuery()

      var total = 0
      if (rsValidacion.next()) {
        total = rsValidacion.getInt("total")
      }

      if (total == 0) {
        val msg =
          "El cliente no puede crear una reseña para este producto porque no lo ha comprado todavía. " +
            "Primero debe realizar un pedido que incluya este producto."
        log.info(
          s"Intento de reseña inválido: cliente $idCliente no ha comprado el producto $idProducto"
        )
        remitente ! ResenaFallida(msg)
        return
      }

      val sqlInsert =
        "INSERT INTO resenas (id_cliente, id_producto, calificacion, comentario, fecha_creacion) " +
          "VALUES (?, ?, ?, ?, NOW())"

      stmtInsert = conexion.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)
      stmtInsert.setInt(1, idCliente)
      stmtInsert.setInt(2, idProducto)
      stmtInsert.setInt(3, calificacion)
      stmtInsert.setString(4, comentario)
      stmtInsert.executeUpdate()

      val rs = stmtInsert.getGeneratedKeys
      if (!rs.next()) {
        throw new RuntimeException("No se pudo obtener el id_resena generado")
      }
      val idResenaGenerada = rs.getInt(1)

      log.info(s"Resenha creada en BD con id_resena=$idResenaGenerada")
      remitente ! ResenaCreada(idResenaGenerada)

    } catch {
      case ex: Exception =>
        log.error(ex, "Error al crear resenha en la base de datos")
        remitente ! ResenaFallida(ex.getMessage)

    } finally {
      if (rsValidacion != null) try rsValidacion.close() catch { case _: Exception => () }
      if (stmtValidacion != null) try stmtValidacion.close() catch { case _: Exception => () }
      if (stmtInsert != null) try stmtInsert.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }

  private def manejarObtenerResenasPorProducto(idProducto: Int): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null

    try {
      conexion = DB.obtenerConexion()
      val sql =
        "SELECT id_cliente, calificacion, comentario " +
          "FROM resenas WHERE id_producto = ? ORDER BY fecha_creacion DESC"

      stmt = conexion.prepareStatement(sql)
      stmt.setInt(1, idProducto)
      rs = stmt.executeQuery()

      var lista = List.empty[(Int, Int, String)]
      while (rs.next()) {
        val idCliente = rs.getInt("id_cliente")
        val calificacion = rs.getInt("calificacion")
        val comentario = rs.getString("comentario")
        lista = lista :+ (idCliente, calificacion, comentario)
      }

      log.info(s"Recuperadas ${lista.size} resenhas para el producto $idProducto")
      remitente ! ResenasPorProducto(idProducto, lista)

    } catch {
      case ex: Exception =>
        log.error(ex, "Error al obtener resenhas de la base de datos")
        remitente ! ResenaFallida(ex.getMessage)

    } finally {
      if (rs != null) try rs.close() catch { case _: Exception => () }
      if (stmt != null) try stmt.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }

  private def manejarObtenerPedidos(): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null

    try {
      conexion = DB.obtenerConexion()
      val sql =
        "SELECT p.id_pedido, p.id_cliente, c.nombre, p.estado " +
          "FROM pedidos p JOIN clientes c ON p.id_cliente = c.id_cliente " +
          "ORDER BY p.id_pedido"

      stmt = conexion.prepareStatement(sql)
      rs = stmt.executeQuery()

      var lista = List.empty[(Int, Int, String, String)]
      while (rs.next()) {
        val idPedido   = rs.getInt("id_pedido")
        val idCliente  = rs.getInt("id_cliente")
        val nombreCli  = rs.getString("nombre")
        val estado     = rs.getString("estado")
        lista = lista :+ (idPedido, idCliente, nombreCli, estado)
      }

      remitente ! Pedidos(lista)

    } catch {
      case ex: Exception =>
        log.error(ex, "Error al obtener pedidos de la base de datos")
        remitente ! PedidoFallido(ex.getMessage)

    } finally {
      if (rs != null) try rs.close() catch { case _: Exception => () }
      if (stmt != null) try stmt.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }

  private def manejarObtenerPedidosPorCliente(idClienteFiltro: Int): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null

    try {
      conexion = DB.obtenerConexion()
      val sql =
        "SELECT id_pedido, fecha_creacion, estado " +
          "FROM pedidos WHERE id_cliente = ? ORDER BY fecha_creacion DESC"

      stmt = conexion.prepareStatement(sql)
      stmt.setInt(1, idClienteFiltro)
      rs = stmt.executeQuery()

      var lista = List.empty[(Int, String, String)]
      while (rs.next()) {
        val idPedido      = rs.getInt("id_pedido")
        val fechaCreacion = rs.getString("fecha_creacion")
        val estado        = rs.getString("estado")
        lista = lista :+ (idPedido, fechaCreacion, estado)
      }

      remitente ! PedidosDeCliente(idClienteFiltro, lista)

    } catch {
      case ex: Exception =>
        log.error(ex, "Error al obtener pedidos por cliente de la base de datos")
        remitente ! PedidoFallido(ex.getMessage)

    } finally {
      if (rs != null) try rs.close() catch { case _: Exception => () }
      if (stmt != null) try stmt.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }

  private def manejarObtenerProductos(): Unit = {
    val remitente = sender()

    var conexion: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null

    try {
      conexion = DB.obtenerConexion()
      val sql = "SELECT id_producto, nombre, precio, cantidad FROM productos ORDER BY id_producto"

      stmt = conexion.prepareStatement(sql)
      rs = stmt.executeQuery()

      var lista = List.empty[(Int, String, BigDecimal, Int)]
      while (rs.next()) {
        val idProducto = rs.getInt("id_producto")
        val nombre     = rs.getString("nombre")
        val precio     = rs.getBigDecimal("precio")
        val cantidad   = rs.getInt("cantidad")
        lista = lista :+ (idProducto, nombre, BigDecimal(precio), cantidad)
      }

      remitente ! Productos(lista)

    } catch {
      case ex: Exception =>
        log.error(ex, "Error al obtener productos de la base de datos")
        // reutilizamos PedidoFallido solo para reportar error genérico
        remitente ! PedidoFallido(ex.getMessage)

    } finally {
      if (rs != null) try rs.close() catch { case _: Exception => () }
      if (stmt != null) try stmt.close() catch { case _: Exception => () }
      if (conexion != null) try conexion.close() catch { case _: Exception => () }
    }
  }
}
