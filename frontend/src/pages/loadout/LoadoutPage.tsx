import { LoadoutPageView } from "./components/LoadoutPageView";
import { useLoadoutPage } from "./hooks/useLoadoutPage";

/** 中文名称：配装页。游戏职责：把配装Hook连接到配装视图组件。 */
export function LoadoutPage() {
  return <LoadoutPageView {...useLoadoutPage()} />;
}
