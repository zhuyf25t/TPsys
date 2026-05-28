import { useEffect, useState, useSyncExternalStore } from "react";
import { getCurrentAuthHandle, getCurrentAuthUser, subscribeAuthState } from "../../../apis/identity/authGateway";
import { getProfileSummary, loadProfileSummary, type ProfileSummary } from "../../../apis/identity/profileGateway";

export interface ProfilePageState {
  resolvedHandle: string;
  profile: ProfileSummary | undefined;
}

export function useProfilePage(routeHandle: string | undefined): ProfilePageState {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const resolvedHandle = routeHandle ?? authUser?.handle ?? getCurrentAuthHandle();
  const [profile, setProfile] = useState<ProfileSummary | undefined>(() => getProfileSummary(resolvedHandle));

  useEffect(() => {
    let cancelled = false;

    void loadProfileSummary(resolvedHandle).then((summary) => {
      if (!cancelled) {
        setProfile(summary);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [resolvedHandle]);

  return {
    resolvedHandle,
    profile
  };
}
