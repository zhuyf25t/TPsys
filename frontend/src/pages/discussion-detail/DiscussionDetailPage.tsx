import { useParams } from "react-router-dom";
import { DiscussionDetailPageView } from "../../components/discussion-detail/DiscussionDetailPageView";
import { useDiscussionDetailPage } from "../../hooks/discussion-detail-page/useDiscussionDetailPage";

/** 中文名称：论坛详情页。游戏职责：把论坛详情Hook连接到论坛详情视图组件。 */
export function DiscussionDetailPage() {
  const { id } = useParams<{ id: string }>();

  return <DiscussionDetailPageView {...useDiscussionDetailPage(id)} />;
}
