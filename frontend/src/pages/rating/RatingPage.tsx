import { RatingPageView } from "./components/RatingPageView";
import { useRatingPage } from "./hooks/useRatingPage";

/** 中文名称：评分页。游戏职责：展示真实账号和战绩生成的 rating 列表。 */
export function RatingPage() {
  return <RatingPageView {...useRatingPage()} />;
}
