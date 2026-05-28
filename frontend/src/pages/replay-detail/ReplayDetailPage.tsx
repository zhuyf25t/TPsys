import { useParams, useSearchParams } from "react-router-dom";
import { ReplayDetailPageView } from "./components/ReplayDetailPageView";
import { useReplayDetailPage } from "./hooks/useReplayDetailPage";

/** 中文名称：回放详情页。游戏职责：把回放详情Hook连接到回放详情视图组件。 */
export function ReplayDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const ratingHandle = searchParams.get("handle")?.trim() || undefined;

  return <ReplayDetailPageView {...useReplayDetailPage(id, ratingHandle)} />;
}
