import { Navigate, Route, Routes } from "react-router-dom";
import { BattlePage } from "../pages/battle/BattlePage";
import { ContributionPage } from "../pages/contribution/ContributionPage";
import { DiscussionDetailPage } from "../pages/discussion-detail/DiscussionDetailPage";
import { DiscussionPage } from "../pages/discussion/DiscussionPage";
import { HomePage } from "../pages/home/HomePage";
import { LoadoutPage } from "../pages/loadout/LoadoutPage";
import { MailsPage } from "../pages/mails/MailsPage";
import { ProfilePage } from "../pages/profile/ProfilePage";
import { RatingPage } from "../pages/rating/RatingPage";
import { ReplayDetailPage } from "../pages/replay-detail/ReplayDetailPage";
import { ReplayPage } from "../pages/replay/ReplayPage";

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
