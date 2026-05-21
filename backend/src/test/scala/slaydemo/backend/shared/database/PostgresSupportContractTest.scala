package slaydemo.backend.shared.database

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import slaydemo.backend.shared.storage.{DatabasePassword, DatabaseUser, JdbcUrl, PostgresConnectionSettings}

object PostgresSupportContractTest {
  def main(args: Array[String]): Unit = {
    hikariConfigUsesTypedConnectionSettings()
    poolKeyUsesCredentialValues()
    poolNameDoesNotExposeCredentials()
    connectionResourceClosesConnection()
    transactionCommitsAndRestoresAutoCommit()
    transactionRollsBackAndRestoresAutoCommit()
    transactionConnectionCommitsClosesAndRestoresAutoCommit()
    transactionConnectionRollsBackClosesAndRestoresAutoCommit()

    println("PostgresSupport contract checks passed")
  }

  private def hikariConfigUsesTypedConnectionSettings(): Unit = {
    val settings = postgresSettings("jdbc:postgresql://localhost:5432/slay_demo", "slay_user", "secret-password")
    val config = PostgresSupport.buildHikariConfig(settings)

    assertEquals("jdbc url is configured", config.getJdbcUrl, settings.jdbcUrl.value)
    assertEquals("database user is configured", config.getUsername, settings.user.map(_.value).orNull)
    assertEquals("database password is configured", config.getPassword, settings.password.map(_.value).orNull)
    assertEquals("pool size is bounded", config.getMaximumPoolSize, 10)
  }

  private def poolKeyUsesCredentialValues(): Unit = {
    val first = postgresSettings("jdbc:postgresql://localhost:5432/slay_demo", "slay_user", "secret-password")
    val second = postgresSettings("jdbc:postgresql://localhost:5432/slay_demo", "slay_user", "secret-password")

    assertEquals("pool key is value based", PostgresConnectionPoolKey.from(first), PostgresConnectionPoolKey.from(second))
  }

  private def poolNameDoesNotExposeCredentials(): Unit = {
    val settings = postgresSettings("jdbc:postgresql://localhost:5432/slay_demo", "slay_user", "secret-password")
    val poolName = PostgresSupport.buildHikariConfig(settings).getPoolName

    assertEquals("pool name hides user", poolName.contains("slay_user"), false)
    assertEquals("pool name hides password", poolName.contains("secret-password"), false)
  }

  private def connectionResourceClosesConnection(): Unit = {
    val connection = RecordingConnection()

    PostgresSupport.connectionResource(IO.pure(connection.proxy)).use(_ => IO.unit).unsafeRunSync()

    assertEquals("resource closes connection", connection.calls, Vector("close"))
  }

  private def transactionCommitsAndRestoresAutoCommit(): Unit = {
    val connection = RecordingConnection()

    val result = PostgresSupport.withTransactionIO(connection.proxy)(IO.pure("created")).unsafeRunSync()

    assertEquals("transaction result", result, "created")
    assertEquals(
      "commit transaction lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "commit", "setAutoCommit(true)")
    )
  }

  private def transactionRollsBackAndRestoresAutoCommit(): Unit = {
    val connection = RecordingConnection()
    val error = RuntimeException("boom")

    try {
      PostgresSupport.withTransactionIO(connection.proxy)(IO.raiseError[String](error)).unsafeRunSync()
      assert(false, "expected transaction failure")
    } catch {
      case thrown: RuntimeException =>
        assertEquals("same transaction failure is rethrown", thrown, error)
    }

    assertEquals(
      "rollback transaction lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "rollback", "setAutoCommit(true)")
    )
  }

  private def transactionConnectionCommitsClosesAndRestoresAutoCommit(): Unit = {
    val connection = RecordingConnection()

    val result = PostgresSupport.withTransactionConnection(connection.proxy)(_ => "saved")

    assertEquals("transaction connection result", result, "saved")
    assertEquals(
      "commit transaction connection lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "commit", "setAutoCommit(true)", "close")
    )
  }

  private def transactionConnectionRollsBackClosesAndRestoresAutoCommit(): Unit = {
    val connection = RecordingConnection()
    val error = RuntimeException("boom")

    try {
      PostgresSupport.withTransactionConnection(connection.proxy)(_ => throw error)
      assert(false, "expected transaction connection failure")
    } catch {
      case thrown: RuntimeException =>
        assertEquals("same transaction connection failure is rethrown", thrown, error)
    }

    assertEquals(
      "rollback transaction connection lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "rollback", "setAutoCommit(true)", "close")
    )
  }

  private def postgresSettings(
    jdbcUrl: String,
    user: String,
    password: String
  ): PostgresConnectionSettings =
    PostgresConnectionSettings(
      jdbcUrl = JdbcUrl(jdbcUrl),
      user = Some(DatabaseUser(user)),
      password = DatabasePassword.fromString(password)
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private final class RecordingConnection(initialAutoCommit: Boolean = true) {
    var autoCommit: Boolean = initialAutoCommit
    var calls: Vector[String] = Vector.empty

    val proxy: Connection =
      Proxy
        .newProxyInstance(
          classOf[Connection].getClassLoader,
          Array(classOf[Connection]),
          RecordingConnectionHandler(this)
        )
        .asInstanceOf[Connection]
  }

  private final class RecordingConnectionHandler(connection: RecordingConnection) extends InvocationHandler {
    override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef =
      method.getName match {
        case "getAutoCommit" =>
          connection.calls = connection.calls :+ "getAutoCommit"
          Boolean.box(connection.autoCommit)
        case "setAutoCommit" =>
          val nextAutoCommit = args(0).asInstanceOf[java.lang.Boolean].booleanValue()
          connection.autoCommit = nextAutoCommit
          connection.calls = connection.calls :+ s"setAutoCommit($nextAutoCommit)"
          null
        case "commit" =>
          connection.calls = connection.calls :+ "commit"
          null
        case "rollback" =>
          connection.calls = connection.calls :+ "rollback"
          null
        case "close" =>
          connection.calls = connection.calls :+ "close"
          null
        case "toString" =>
          "RecordingConnection"
        case "hashCode" =>
          Int.box(System.identityHashCode(proxy))
        case "equals" =>
          Boolean.box(proxy eq args(0))
        case _ =>
          defaultReturn(method.getReturnType)
      }
  }

  private def defaultReturn(returnType: Class[?]): AnyRef =
    if returnType == java.lang.Boolean.TYPE then Boolean.box(false)
    else if returnType == java.lang.Byte.TYPE then Byte.box(0.toByte)
    else if returnType == java.lang.Short.TYPE then Short.box(0.toShort)
    else if returnType == java.lang.Integer.TYPE then Int.box(0)
    else if returnType == java.lang.Long.TYPE then Long.box(0L)
    else if returnType == java.lang.Float.TYPE then Float.box(0.0f)
    else if returnType == java.lang.Double.TYPE then Double.box(0.0d)
    else if returnType == java.lang.Character.TYPE then Char.box(0.toChar)
    else null
}
