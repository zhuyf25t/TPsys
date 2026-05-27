package route.battle

import cats.effect.{IO, Resource}

import java.sql.Connection

import services.battle.database.results.BattleResultRepository

enum BattleHttp4sResultBackend {
  case ConnectionBacked(connectionResource: Resource[IO, Connection])
  case RepositoryBacked(resultRepository: BattleResultRepository)
}
