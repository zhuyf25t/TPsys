import { RatingPageView } from "../../components/rating/RatingPageView";
import { useRatingPage } from "../../hooks/rating-page/useRatingPage";

/** 中文名称：评分页。游戏职责：展示真实账号和战绩生成的 rating 列表。 */
export function RatingPage() {
  return <RatingPageView {...useRatingPage()} />;
}
