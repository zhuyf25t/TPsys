package system.api

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection

object UnsupportedAPIConnection:
  def create: Connection =
    Proxy
      .newProxyInstance(
        classOf[Connection].getClassLoader,
        Array(classOf[Connection]),
        handler
      )
      .asInstanceOf[Connection]

  private val handler: InvocationHandler =
    (_: AnyRef, method: Method, _: Array[AnyRef]) =>
      method.getName match
        case "close" =>
          ()
        case "isClosed" =>
          java.lang.Boolean.TRUE
        case "toString" =>
          "UnsupportedConnection(APIMessage plan compatibility)"
        case "isWrapperFor" =>
          java.lang.Boolean.FALSE
        case "unwrap" =>
          throw UnsupportedOperationException("No JDBC connection is configured for this API route.")
        case _ =>
          throw UnsupportedOperationException("No JDBC connection is configured for this API route.")
