import { useParams } from "react-router-dom";
import { ProfilePageView } from "../../components/profile/ProfilePageView";
import { useProfilePage } from "../../hooks/profile-page/useProfilePage";

/** 中文名称：玩家档案页。游戏职责：展示玩家身份、真实战局、评分变化和近期表现。 */
export function ProfilePage() {
  const { handle } = useParams<{ handle: string }>();
  return <ProfilePageView {...useProfilePage(handle)} />;
}
