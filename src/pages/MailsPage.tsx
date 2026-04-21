import { useEffect, useState, useSyncExternalStore } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../features/auth/authGateway";
import {
  getLocalBattleMailSummaries,
  getMailSummaries,
  loadRemoteMailSummaries,
  markMailAsRead,
  markMailAsReadRemote,
  type MailSummary
} from "../features/mails/mailsGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";

export function MailsPage() {
  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const [mailSummaries, setMailSummaries] = useState<MailSummary[]>(() => getMailSummaries());
  const [mailSource, setMailSource] = useState<"local" | "remote">("local");

  useEffect(() => {
    let cancelled = false;
    const fallback = getMailSummaries();
    setMailSummaries(fallback);
    setMailSource("local");

    const ownerHandle = currentUser?.handle?.trim();
    if (!ownerHandle) {
      return () => {
        cancelled = true;
      };
    }

    void loadRemoteMailSummaries(ownerHandle).then((remoteSummaries) => {
      if (cancelled || !remoteSummaries) {
        return;
      }

      setMailSummaries([...remoteSummaries, ...getLocalBattleMailSummaries()]);
      setMailSource("remote");
    });

    return () => {
      cancelled = true;
    };
  }, [currentUser?.handle]);

  const unreadCount = mailSummaries.filter((mail) => mail.unread).length;
  const importantCount = mailSummaries.filter((mail) => mail.important).length;

  const handleMailClick = async (mailId: string, unread: boolean): Promise<void> => {
    if (!unread) {
      return;
    }

    const ownerHandle = currentUser?.handle?.trim() ?? "";
    const isBattleMail = mailId.startsWith("battle:");

    if (mailSource === "remote" && ownerHandle && !isBattleMail) {
      const ok = await markMailAsReadRemote(ownerHandle, mailId);
      if (ok) {
        const remoteSummaries = await loadRemoteMailSummaries(ownerHandle);
        if (remoteSummaries) {
          setMailSummaries([...remoteSummaries, ...getLocalBattleMailSummaries()]);
          return;
        }
      }
    }

    if (markMailAsRead(mailId)) {
      setMailSummaries(getMailSummaries());
      if (mailSource === "remote" && ownerHandle) {
        const remoteSummaries = await loadRemoteMailSummaries(ownerHandle);
        if (remoteSummaries) {
          setMailSummaries([...remoteSummaries, ...getLocalBattleMailSummaries()]);
        }
      }
    }
  };

  return (
    <ShellLayout title="站内信" subtitle="好友申请、战报和结算通知会在这里汇总。">
      <section className="detail-card">
        <h3>收件箱概览</h3>
        <div className="pill-row">
          <span className="pill">未读 {unreadCount}</span>
          <span className="pill">重要 {importantCount}</span>
          <span className="pill">总计 {mailSummaries.length}</span>
        </div>
      </section>

      {mailSummaries.length === 0 ? (
        <section className="detail-card empty-state">
          <h3>收件箱还是空的</h3>
          <p>完成一局、发起好友申请或收到裁决后，这里就会出现通知。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/battle">
              进入战斗
            </Link>
          </div>
        </section>
      ) : (
        <section className="mail-list">
          {mailSummaries.map((mail) => (
            <button
              key={mail.id}
              type="button"
              className={`mail-card${mail.unread ? " mail-card--unread" : " mail-card--read"}`}
              onClick={() => {
                void handleMailClick(mail.id, mail.unread);
              }}
              aria-label={`${mail.subject}${mail.unread ? "，未读" : "，已读"}`}
            >
              <div className="mail-card__meta">
                <span className={`mail-card__flag mail-card__flag--${mail.kind}`}>
                  {mail.kind === "battle"
                    ? "战报"
                    : mail.kind === "reward"
                      ? "奖励"
                      : mail.kind === "friend"
                        ? "好友"
                        : mail.kind === "governance"
                          ? "裁决"
                          : "通知"}
                </span>
                {mail.unread ? <span className="mail-card__dot" aria-label="未读邮件" /> : null}
                {mail.unread ? <span className="mail-card__status mail-card__status--unread">未读</span> : null}
                {!mail.unread ? <span className="mail-card__status mail-card__status--read">已读</span> : null}
                {mail.important ? <span className="mail-card__status">重要</span> : null}
                <small>{mail.senderLabel}</small>
                <small>{mail.receivedLabel}</small>
              </div>
              <strong>{mail.subject}</strong>
              <span>{mail.excerpt}</span>
            </button>
          ))}
        </section>
      )}
    </ShellLayout>
  );
}
