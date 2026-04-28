import { Navigate, Route, Routes } from "react-router-dom";
import { BattlePage } from "../pages/BattlePage";
import { ContributionPage } from "../pages/ContributionPage";
import { DiscussionDetailPage } from "../pages/DiscussionDetailPage";
import { DiscussionPage } from "../pages/DiscussionPage";
import { HomePage } from "../pages/HomePage";
import { LoadoutPage } from "../pages/LoadoutPage";
import { MailsPage } from "../pages/MailsPage";
import { ProfilePage } from "../pages/ProfilePage";
import { RatingPage } from "../pages/RatingPage";
import { ReplayDetailPage } from "../pages/ReplayDetailPage";
import { ReplayPage } from "../pages/ReplayPage";
import { AuthSessionBootstrap } from "../shared/ui/AuthSessionBootstrap";

export function App() {
  return (
    <>
      <AuthSessionBootstrap />
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
    </>
  );
}
