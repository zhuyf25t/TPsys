package slaydemo.backend

import slaydemo.backend.battle.services.{
  BattleFinishProjectionPlanContractTest,
  BattleFinishProjectionWriteContractTest,
  BattleSkillRulesContractTest
}
import slaydemo.backend.battle.routes.BattleCommandRouteContractTest
import slaydemo.backend.shared.storage.StorageConfigContractTest

object BackendContractTestRunner {
  def main(args: Array[String]): Unit = {
    run("repository wiring", () => BackendRepositoryWiringContractTest.main(Array.empty))
    run("route contexts", () => BackendRouteContextContractTest.main(Array.empty))
    run("battle result service", () => BattleResultServiceContractTest.main(Array.empty))
    run("battle command route", () => BattleCommandRouteContractTest.main(Array.empty))
    run("battle skill rules", () => BattleSkillRulesContractTest.main(Array.empty))
    run("battle state runtime", () => BattleStateRuntimeContractTest.main(Array.empty))
    run("bot profile service", () => BotProfileServiceContractTest.main(Array.empty))
    run("battle queue authorization", () => BattleQueueAuthorizationContractTest.main(Array.empty))
    run("battle queue runtime", () => BattleQueueRuntimeContractTest.main(Array.empty))
    run("friend request service", () => FriendRequestServiceContractTest.main(Array.empty))
    run("forum service", () => ForumServiceContractTest.main(Array.empty))
    run("governance service", () => GovernanceServiceContractTest.main(Array.empty))
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
