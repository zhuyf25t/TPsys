import { FriendsPageView } from "./components/FriendsPageView";
import { useFriendsPage } from "./hooks/useFriendsPage";

/** 中文名称：好友页。游戏职责：连接好友请求状态和好友列表视图。 */
export function FriendsPage() {
  return <FriendsPageView {...useFriendsPage()} />;
}
