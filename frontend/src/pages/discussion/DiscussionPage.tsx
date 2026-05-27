import { DiscussionPageView } from "../../components/discussion/DiscussionPageView";
import { useDiscussionPage } from "../../hooks/discussion-page/useDiscussionPage";

/** 中文名称：论坛列表页。游戏职责：把论坛列表Hook连接到论坛视图组件。 */
export function DiscussionPage() {
  return <DiscussionPageView {...useDiscussionPage()} />;
}
