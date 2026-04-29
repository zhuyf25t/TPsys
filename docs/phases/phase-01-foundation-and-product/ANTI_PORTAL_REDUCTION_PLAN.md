# Anti-Portal Reduction Plan

## 1. Why The Frontend Still Read Too Much Like A Web Portal

Before this reduction pass, the UI still carried several web-product habits:

- too many same-weight entry cards on the home route
- too much explanatory copy on the home route
- battle shell status surfaces that still looked like page cards
- social entry semantics borrowed from other data instead of staying quiet
- too many visible branch routes competing with the main play loop

This meant the product still felt like:

- a polished portal
- a dashboard with battle inside it

rather than:

- a game main menu
- a game lobby
- an in-battle UI shell with secondary overlays

---

## 2. The 15 Least Welcome Elements On The Home Route

These were the main over-designed web UI elements that needed removal, reduction, or demotion:

1. same-weight route grid dominating the page
2. too many equal CTA surfaces
3. overly explanatory subtitle copy
4. highlight cards explaining the system instead of pushing the player forward
5. a visible "browse modules" hierarchy
6. replay / mails / rating / contribution / profile / discussion competing with battle for first attention
7. too much text around the hero section
8. too many descriptive micro-blocks in the first screen
9. a structure that suggested portal browsing before play
10. too much clean SaaS-like spacing rhythm
11. home panels that read like information cards rather than game-hall hints
12. secondary destinations appearing too central
13. a masthead + content rhythm that still leaned product-site
14. too much visible explanation of the player loop
15. insufficient visual dominance for the main "start next match" action

---

## 3. The 15 Least Welcome Web-Like Elements Inside `/battle`

These were the most web-like battle-shell elements that needed reduction or reframing:

1. large page-like status card feel
2. quick actions that looked like web pills
3. settlement that risked reading like a results section rather than a game overlay
4. too much descriptive copy inside battle shell
5. an overly legible page-panel tone instead of game frame tone
6. drawer panels that still felt too much like clean web modals
7. right-side social semantics borrowing discussion content
8. too many visible text explanations of battle structure
9. previous match record reading like a page widget
10. too much page-chrome feeling around runtime-adjacent UI
11. too much typographic weight compared with frame/material weight
12. not enough distinction between in-game quick access and full-page reading destinations
13. too much rectangular card logic
14. not enough HUD-like grouping feel
15. post-match action area not ceremonial enough

---

## 4. What Was Deleted, Hidden, Demoted, Or Folded

### Home

The following were reduced or demoted:

- same-weight portal behavior
- strong route-matrix dominance
- explanatory blocks as primary content
- secondary route surfaces moved behind a clearer main loop

The page now foregrounds:

- player identity
- current loadout / skills
- next match CTA

### Battle

The following were reduced or corrected:

- discussion data pretending to be social notifications
- web-pill feeling in quick buttons
- overly page-like shell framing

The shell now keeps:

- only the in-battle quick entries
- settlement overlay
- minimal supporting shell surfaces

---

## 5. What Must Disappear From The Main Surface

These should not dominate the first visible layer:

- replay library as a major first-screen module
- mails inbox as a major first-screen module
- rating board as a major first-screen module
- contribution as a major first-screen module
- profile as a major first-screen module
- discussion as a major first-screen module
- system-explaining cards
- route-browsing copy

They may still exist as:

- secondary hall branches
- in-battle drawers
- `View all` reading/archive pages

---

## 6. What Now Belongs In Secondary Menus Or Overlays

### In battle overlays

- replay quick view
- discussion quick view
- rating quick view
- mails quick view
- social quiet state
- settlement overlay

### Full pages after explicit `View all`

- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`

---

## 7. Current Reduction Outcome

This reduction pass was not about adding more surfaces.
It was about:

- removing equal-weight route competition
- hiding web-like branch logic behind the main play loop
- making battle and player identity dominate
- turning quick surrounding actions into overlays or secondary routes

The product now behaves much more like:

- a game main menu
- a game lobby
- an in-battle shell

and much less like:

- a structured feature portal
- a route dashboard
