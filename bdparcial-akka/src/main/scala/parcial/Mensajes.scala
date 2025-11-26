package parcial


case class DetallePedidoData(idProducto: Int, cantidad: Int)

case class CrearPedido(idCliente: Int, detalles: List[DetallePedidoData])

case class PedidoCreado(idPedido: Int)

case class PedidoFallido(razon: String)


// ----- Consultas de pedidos -----
case object ObtenerPedidos

// (idPedido, idCliente, nombreCliente, estado)
case class Pedidos(lista: List[(Int, Int, String, String)])

case class ObtenerPedidosPorCliente(idCliente: Int)

// (idPedido, fechaCreacion, estado)
case class PedidosDeCliente(idCliente: Int, lista: List[(Int, String, String)])

// ----- Consultas de productos -----

case object ObtenerProductos

// (idProducto, nombre, precio, cantidad)
case class Productos(lista: List[(Int, String, BigDecimal, Int)])


case class CrearResena(
  idCliente: Int,
  idProducto: Int,
  calificacion: Int,
  comentario: String
)

case class ResenaCreada(idResena: Int)

case class ResenaFallida(razon: String)

case class ObtenerResenasPorProducto(idProducto: Int)

case class ResenasPorProducto(
  idProducto: Int,
  resenas: List[(Int, Int, String)] // (idCliente, calificacion, comentario)
)

// ----- Facturación -----
case object ObtenerFacturasPorCliente
case class ObtenerFacturaCliente(idCliente: Int)

case class FacturaCliente(
  idCliente: Int,
  nombreCliente: String,
  pedidos: List[(Int, String, BigDecimal)], // (idPedido, fecha, total)
  totalGeneral: BigDecimal
)

case class FacturaFallida(razon: String)
