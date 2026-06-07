import { Navigate, Route, Routes } from "react-router-dom";
import { BattlePage } from "../pages/battle";
import { ContributionPage } from "../pages/contribution";
import { DiscussionDetailPage } from "../pages/discussion-detail";
import { DiscussionPage } from "../pages/discussion";
import { FriendsPage } from "../pages/friends";
import { HomePage } from "../pages/home";
import { LoadoutPage } from "../pages/loadout";
import { MailsPage } from "../pages/mails";
import { ProfilePage } from "../pages/profile";
import { RatingPage } from "../pages/rating";
import { ReplayDetailPage } from "../pages/replay-detail";
import { ReplayPage } from "../pages/replay";

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
      <Route path="/friends" element={<FriendsPage />} />
      <Route path="/profile/:handle" element={<ProfilePage />} />
      <Route path="/discussion" element={<DiscussionPage />} />
      <Route path="/discussion/:id" element={<DiscussionDetailPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
