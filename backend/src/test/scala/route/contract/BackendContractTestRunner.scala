package route.contract

object BackendContractTestRunner:
  def main(args: Array[String]): Unit =
    run("storage config", StorageConfigContractTest.run)
    run("repository wiring", BackendRepositoryWiringContractTest.run)
    run("password hasher", PasswordHasherContractTest.run)
    run("postgres support", PostgresSupportContractTest.run)
    run("postgres repository boundary", PostgresRepositoryBoundaryContractTest.run)
    run("replay file repository", ReplayFileRepositoryContractTest.run)
    run("health http4s route", HealthHttp4sRouteContractTest.run)
    run("identity http4s route", IdentityHttp4sRouteContractTest.run)
    run("battle queue http4s route", BattleQueueHttp4sRouteContractTest.run)
    run("battle room http4s route", BattleRoomHttp4sRouteContractTest.run)
    run("battle state http4s route", BattleStateHttp4sRouteContractTest.run)
    run("battle command http4s route", BattleCommandHttp4sRouteContractTest.run)
    run("battle state runtime", BattleStateRuntimeContractTest.run)
    run("battle finish projection", BattleFinishProjectionContractTest.run)
    run("battle result http4s route", BattleResultHttp4sRouteContractTest.run)
    run("mail http4s route", MailHttp4sRouteContractTest.run)
    run("social http4s route", SocialHttp4sRouteContractTest.run)
    run("forum http4s route", ForumHttp4sRouteContractTest.run)
    run("governance http4s route", GovernanceHttp4sRouteContractTest.run)
    run("replay http4s route", ReplayHttp4sRouteContractTest.run)
    run("bot profile http4s route", BotProfileHttp4sRouteContractTest.run)
    println("Backend contract checks passed")

  private def run(label: String, check: () => Unit): Unit =
    println(s"Running $label contract checks")
    check()
