import { HomePageView } from "../../components/home/HomePageView";
import { useHomePage } from "../../hooks/home-page/useHomePage";

/** 中文名称：首页。游戏职责：把大厅首页Hook连接到大厅视图组件。 */
export function HomePage() {
  return <HomePageView {...useHomePage()} />;
}
