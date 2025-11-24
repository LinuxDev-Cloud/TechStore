package parcial

import java.sql.{Connection, DriverManager}

object DB {

  private val url      = "jdbc:mysql://localhost:3306/bdparcial?serverTimezone=UTC&useSSL=false"
  private val user     = "root"
  private val password = "1327"

  def obtenerConexion(): Connection = {
    DriverManager.getConnection(url, user, password)
  }
}
