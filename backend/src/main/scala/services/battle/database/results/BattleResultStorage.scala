package services.battle.database.results

enum BattleResultStorage {
  case ConnectionTable
  case Repository(resultRepository: BattleResultRepository)
}
