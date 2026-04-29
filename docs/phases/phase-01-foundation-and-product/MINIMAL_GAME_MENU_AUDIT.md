# Minimal Game Menu Audit

## Home: 20 Elements That Should Be Removed, Hidden, Or Demoted

1. full top nav with too many route buttons
2. large subtitle paragraph under the page title
3. multiple same-weight CTA buttons
4. route-grid style secondary card matrix
5. "次级入口" explanatory header copy
6. portal-card labels that explain the system
7. replay card as a first-screen equal-weight destination
8. mails card as a first-screen equal-weight destination
9. rating card as a first-screen equal-weight destination
10. contribution card as a first-screen equal-weight destination
11. profile card as a first-screen equal-weight destination
12. discussion card as a first-screen equal-weight destination
13. the three explanatory lobby-strip cards
14. recent replay list on the main menu screen
15. latest discussion list on the main menu screen
16. contribution summary block on the main menu screen
17. four separate identity/info cards for the player loop
18. highlight cards that explain the loop instead of letting layout express it
19. too much hero copy
20. any first-screen element that competes with `开始 / 进入下一局`

## `/battle`: 20 Web-Like Elements That Should Be Removed, Hidden, Or Demoted

1. large descriptive status-card paragraph
2. oversized quick buttons
3. web-like panel proportions in the shell
4. matching overlay body text longer than a game menu needs
5. previous-match chip reading like a page widget
6. drawer header paragraphs that explain too much
7. drawer rows with too much metadata
8. large empty-state copy inside drawers
9. settlement overlay copy that feels like a web results page
10. too many visible text labels competing with runtime
11. too much page-card spacing around battle shell surfaces
12. oversized chrome around quick strips
13. overly soft, app-like pills and rounded web buttons
14. any route-like CTA inside battle shell before `View all`
15. too much explanatory copy in matching state
16. too much explanatory copy in live state
17. too much explanatory copy in settled state
18. any large module that looks detached from the runtime
19. any secondary destination that can be reduced to a smaller corner entry
20. any UI surface that makes the player feel they are on a website instead of in a game menu

## Buttons To Keep

### Home

- 开始 / 进入下一局
- 配装
- one secondary branch button only: 回放 or 社区

### Battle

- left-top: 回放 / 讨论 / 排行
- right-bottom: 站内信 / 社交通知
- settlement: 再来一局 / 查看回放 / 查看变化 / 返回大厅

## Buttons That Must Disappear Or Be Demoted

- any full route matrix on the home first screen
- multi-row route CTA sets on the home first screen
- extra navigation buttons beyond the minimal lobby flow
- page-like big buttons inside battle shell
- explanatory CTA chains inside battle overlays

## What Must Move Out Of The First Screen

- replay library lists
- mails list
- rating board
- contribution summary page content
- profile page content
- discussion listing

These should live in:

- secondary routes
- `View all` pages
- or smaller, quieter branch links

## Current Reduction Goal

The home route should become:

- player panel
- current loadout
- strongest start button
- one or two smaller branches at most

The battle route should become:

- runtime
- tiny corner entries
- compact settlement
- `View all` for full reading surfaces
