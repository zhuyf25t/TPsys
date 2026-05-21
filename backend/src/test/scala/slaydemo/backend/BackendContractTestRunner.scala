package slaydemo.backend

import slaydemo.backend.battle.services.{
  BattleFinishProjectionPlanContractTest,
  BattleFinishProjectionWriteContractTest,
  BattleSkillRulesContractTest
}
import slaydemo.backend.battle.routes.{
  BattleCommandRouteContractTest,
  BattleJoinRouteContractTest,
  BattleRoomStateRouteContractTest
}
import slaydemo.backend.http4s.{
  BackendHttp4sRoutesCompositionContractTest,
  BotProfileHttp4sContractTest,
  BattleCommandHttp4sContractTest,
  BattleQueueHttp4sJoinContractTest,
  BattleQueueHttp4sLeaveContractTest,
  BattleQueueHttp4sStatusContractTest,
  BattleResultHttp4sContractTest,
  BattleRoomHttp4sContractTest,
  BattleStateReadHttp4sContractTest,
  BattleStateStreamHttp4sContractTest,
  ForumHttp4sContractTest,
  GovernanceHttp4sContractTest,
  HealthHttp4sRouteContractTest,
  IdentityHttp4sContractTest,
  MailHttp4sContractTest,
  ReplayHttp4sCatalogContractTest,
  SocialHttp4sContractTest
}
import slaydemo.backend.identity.ports.PasswordHasherContractTest
import slaydemo.backend.shared.database.{PostgresRepositoryBoundaryContractTest, PostgresSupportContractTest}
import slaydemo.backend.shared.storage.StorageConfigContractTest

object BackendContractTestRunner {
  def main(args: Array[String]): Unit = {
    run("repository wiring", () => BackendRepositoryWiringContractTest.main(Array.empty))
    run("backend environment", () => BackendEnvironmentContractTest.main(Array.empty))
    run("backend API boundaries", () => BackendApiBoundaryContractTest.main(Array.empty))
    run("postgres support", () => PostgresSupportContractTest.main(Array.empty))
    run("postgres repository boundaries", () => PostgresRepositoryBoundaryContractTest.main(Array.empty))
    run("backend http4s route composition", () => BackendHttp4sRoutesCompositionContractTest.main(Array.empty))
    run("health http4s route", () => HealthHttp4sRouteContractTest.main(Array.empty))
    run("identity http4s route", () => IdentityHttp4sContractTest.main(Array.empty))
    run("mail http4s route", () => MailHttp4sContractTest.main(Array.empty))
    run("replay http4s catalog", () => ReplayHttp4sCatalogContractTest.main(Array.empty))
    run("social http4s route", () => SocialHttp4sContractTest.main(Array.empty))
    run("forum http4s route", () => ForumHttp4sContractTest.main(Array.empty))
    run("governance http4s route", () => GovernanceHttp4sContractTest.main(Array.empty))
    run("bot profile http4s", () => BotProfileHttp4sContractTest.main(Array.empty))
    run("battle queue http4s join", () => BattleQueueHttp4sJoinContractTest.main(Array.empty))
    run("battle queue http4s leave", () => BattleQueueHttp4sLeaveContractTest.main(Array.empty))
    run("battle queue http4s status", () => BattleQueueHttp4sStatusContractTest.main(Array.empty))
    run("battle room http4s", () => BattleRoomHttp4sContractTest.main(Array.empty))
    run("battle state read http4s", () => BattleStateReadHttp4sContractTest.main(Array.empty))
    run("battle state stream http4s", () => BattleStateStreamHttp4sContractTest.main(Array.empty))
    run("battle command http4s", () => BattleCommandHttp4sContractTest.main(Array.empty))
    run("battle result http4s", () => BattleResultHttp4sContractTest.main(Array.empty))
    run("battle result service", () => BattleResultServiceContractTest.main(Array.empty))
    run("battle command route", () => BattleCommandRouteContractTest.main(Array.empty))
    run("battle join route", () => BattleJoinRouteContractTest.main(Array.empty))
    run("battle room/state route", () => BattleRoomStateRouteContractTest.main(Array.empty))
    run("battle skill rules", () => BattleSkillRulesContractTest.main(Array.empty))
    run("battle state runtime", () => BattleStateRuntimeContractTest.main(Array.empty))
    run("bot profile service", () => BotProfileServiceContractTest.main(Array.empty))
    run("battle queue authorization", () => BattleQueueAuthorizationContractTest.main(Array.empty))
    run("battle queue runtime", () => BattleQueueRuntimeContractTest.main(Array.empty))
    run("friend request service", () => FriendRequestServiceContractTest.main(Array.empty))
    run("forum service", () => ForumServiceContractTest.main(Array.empty))
    run("governance service", () => GovernanceServiceContractTest.main(Array.empty))
    run("password hasher", () => PasswordHasherContractTest.main(Array.empty))
    run("identity service", () => IdentityServiceContractTest.main(Array.empty))
    run("mail service", () => MailServiceContractTest.main(Array.empty))
    run("replay service", () => ReplayServiceContractTest.main(Array.empty))
    run("storage config", () => StorageConfigContractTest.main(Array.empty))
    run("battle finish projection plan", () => BattleFinishProjectionPlanContractTest.main(Array.empty))
    run("battle finish projection writes", () => BattleFinishProjectionWriteContractTest.main(Array.empty))
    run("visitor handle guardrails", () => VisitorHandleGuardrailContractTest.main(Array.empty))

    println("Backend contract checks passed")
  }

  private def run(label: String, check: () => Unit): Unit = {
    println(s"Running $label contract checks")
    check()
  }
}
