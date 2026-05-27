import { ContributionPageView } from "../../components/contribution/ContributionPageView";
import { useContributionPage } from "../../hooks/contribution-page/useContributionPage";

/** 中文名称：贡献页。游戏职责：展示真实账号、战报和治理调整生成的 contribution 列表。 */
export function ContributionPage() {
  return <ContributionPageView {...useContributionPage()} />;
}
