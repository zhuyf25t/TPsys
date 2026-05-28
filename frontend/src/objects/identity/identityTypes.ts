export type PlayerHandle = string;
export type DisplayName = string;
export type SessionToken = string;
export type SkinIdDto = "blue" | "survivor" | "soldier" | "old";

export interface IdentityRegistrationApiRequestDto {
  handle?: string;
  password?: string;
  skinId?: string;
}

export interface IdentitySessionApiRequestDto {
  handle?: string;
  password?: string;
}

export interface IdentityCurrentApiRequestDto {
  session?: string;
}

export interface IdentityAuthResponseDto {
  handle: PlayerHandle;
  skinId: SkinIdDto;
  session: SessionToken;
}

export interface IdentityAccountSummaryDto {
  handle: PlayerHandle;
  displayName: DisplayName;
  skinId: SkinIdDto;
}

export interface IdentityAccountsResponseDto {
  accounts: IdentityAccountSummaryDto[];
}
