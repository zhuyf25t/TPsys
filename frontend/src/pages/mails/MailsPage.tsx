import { MailsPageView } from "../../components/mails/MailsPageView";
import { useMailsPage } from "../../hooks/mails-page/useMailsPage";

/** 中文名称：站内信页。游戏职责：把站内信Hook连接到站内信视图组件。 */
export function MailsPage() {
  return <MailsPageView {...useMailsPage()} />;
}
