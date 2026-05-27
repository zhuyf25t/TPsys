import { ReplayPageView } from "../../components/replay/ReplayPageView";
import { useReplayPage } from "../../hooks/replay-page/useReplayPage";

/** 中文名称：回放页。游戏职责：把回放页面Hook连接到回放视图组件。 */
export function ReplayPage() {
  return <ReplayPageView {...useReplayPage()} />;
}
