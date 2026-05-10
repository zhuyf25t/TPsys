package slaydemo.backend

import slaydemo.backend.battle.services.{
  BattleFinishProjectionPlanContractTest,
  BattleFinishProjectionWriteContractTest,
  BattleSkillRulesContractTest
}
import slaydemo.backend.battle.routes.{
  BattleCommandRouteContractTest,
  BattleJoinRouteContractTest,
  BattleResultRouteContractTest,
  BattleRoomStateRouteContractTest
}
import slaydemo.backend.bots.routes.BotProfileRouteContractTest
import slaydemo.backend.forum.routes.ForumRouteContractTest
import slaydemo.backend.governance.routes.GovernanceRouteContractTest
import slaydemo.backend.identity.routes.IdentityRouteContractTest
import slaydemo.backend.mail.routes.MailRouteContractTest
import slaydemo.backend.replay.routes.ReplayRouteContractTest
import slaydemo.backend.shared.routes.HealthRouteContractTest
import slaydemo.backend.shared.storage.StorageConfigContractTest
import slaydemo.backend.social.routes.SocialRouteContractTest

object BackendContractTestRunner {
  def main(args: Array[String]): Unit = {
    run("repository wiring", () => BackendRepositoryWiringContractTest.main(Array.empty))
    run("backend environment", () => BackendEnvironmentContractTest.main(Array.empty))
    run("route contexts", () => BackendRouteContextContractTest.main(Array.empty))
    run("health route", () => HealthRouteContractTest.main(Array.empty))
    run("battle result route", () => BattleResultRouteContractTest.main(Array.empty))
    run("battle result service", () => BattleResultServiceContractTest.main(Array.empty))
    run("battle command route", () => BattleCommandRouteContractTest.main(Array.empty))
    run("battle join route", () => BattleJoinRouteContractTest.main(Array.empty))
    run("battle room/state route", () => BattleRoomStateRouteContractTest.main(Array.empty))
    run("battle skill rules", () => BattleSkillRulesContractTest.main(Array.empty))
    run("battle state runtime", () => BattleStateRuntimeContractTest.main(Array.empty))
    run("bot profile route", () => BotProfileRouteContractTest.main(Array.empty))
    run("bot profile service", () => BotProfileServiceContractTest.main(Array.empty))
    run("battle queue authorization", () => BattleQueueAuthorizationContractTest.main(Array.empty))
    run("battle queue runtime", () => BattleQueueRuntimeContractTest.main(Array.empty))
    run("friend request service", () => FriendRequestServiceContractTest.main(Array.empty))
    run("forum route", () => ForumRouteContractTest.main(Array.empty))
    run("forum service", () => ForumServiceContractTest.main(Array.empty))
    run("governance route", () => GovernanceRouteContractTest.main(Array.empty))
    run("governance service", () => GovernanceServiceContractTest.main(Array.empty))
    run("identity route", () => IdentityRouteContractTest.main(Array.empty))
    run("identity service", () => IdentityServiceContractTest.main(Array.empty))
    run("mail route", () => MailRouteContractTest.main(Array.empty))
    run("mail service", () => MailServiceContractTest.main(Array.empty))
    run("replay route", () => ReplayRouteContractTest.main(Array.empty))
    run("replay service", () => ReplayServiceContractTest.main(Array.empty))
    run("social route", () => SocialRouteContractTest.main(Array.empty))
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
