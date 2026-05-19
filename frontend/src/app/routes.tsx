import { Navigate, Route, Routes } from "react-router-dom";
import { BattlePage } from "../domains/battle/pages/battle/BattlePage";
import { LoadoutPage } from "../domains/battle/pages/loadout/LoadoutPage";
import { DiscussionDetailPage } from "../domains/forum/pages/discussion-detail/DiscussionDetailPage";
import { DiscussionPage } from "../domains/forum/pages/discussion-list/DiscussionPage";
import { ContributionPage } from "../domains/governance/pages/contribution/ContributionPage";
import { RatingPage } from "../domains/governance/pages/rating/RatingPage";
import { ProfilePage } from "../domains/identity/pages/profile/ProfilePage";
import { MailsPage } from "../domains/mail/pages/inbox/MailsPage";
import { ReplayDetailPage } from "../domains/replay/pages/replay-detail/ReplayDetailPage";
import { ReplayPage } from "../domains/replay/pages/replay-list/ReplayPage";
import { HomePage } from "./pages/home/HomePage";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/loadout" element={<LoadoutPage />} />
      <Route path="/battle" element={<BattlePage />} />
      <Route path="/replay" element={<ReplayPage />} />
      <Route path="/replay/:id" element={<ReplayDetailPage />} />
      <Route path="/mails" element={<MailsPage />} />
      <Route path="/rating" element={<RatingPage />} />
      <Route path="/contribution" element={<ContributionPage />} />
      <Route path="/profile/:handle" element={<ProfilePage />} />
      <Route path="/discussion" element={<DiscussionPage />} />
      <Route path="/discussion/:id" element={<DiscussionDetailPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
