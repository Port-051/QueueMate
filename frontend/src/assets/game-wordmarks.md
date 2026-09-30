# Game wordmark sources

Downloaded 2026-09-12. Used to identify the game selected for matchmaking.

- `game-wordmark-lol.png`: [Riot Games press assets](https://www.riotgames.com/en/press), [800px League of Legends logo](https://www.riotgames.com/darkroom/800/9c08e3b3ce6281f252a4dfbf61357a11:75ab7e60b85225c6c7f19ae4024e27a6/lol-logo-rendered-hi-res.png).
- `game-wordmark-valorant.png`: [Riot Games press assets](https://www.riotgames.com/en/press), [VALORANT logo archive](https://www.riotgames.com/darkroom/original/529d413f0d04b52fa43ac4e0afae762a:5079209ca6c46089fe07ff56ed1cf3d1/valorant-logos.zip), `VALORANT Logos/Logotype/V_Logotype_Off-White.png`.
- `game-wordmark-pubg.webp`: [PUBG official website](https://www.pubg.com/en/main), [header wordmark](https://wstatic-prod.pubg.com/web/live/main_49267b7/img/1de035e.webp).

The original image files are unmodified. The home page pairs them with the project's existing game background artwork.

## Compact game icons

All game badges, including reservations, profiles, onboarding, the landing page, and party rooms, use the shared `GameBadge` component.

- `game-icon-lol.svg`: [League of Legends official website](https://www.leagueoflegends.com/en-us/), [site icon](https://cmsassets.rgpub.io/sanity/images/dsfx7636/news_live/d3b7bd9decb1e1672dcb80be4f8bc1aa05490dc1-110x70.svg?accountingTag=LoL). The SVG viewBox is tightened around the existing paths; the mark itself is unchanged.
- `game-icon-valorant.png`: the Riot archive above, `VALORANT Logos/Logomark/V_Logomark_Red.png`. CSS accounts for transparent padding in the original image.
- `game-icon-pubg.png`: [PUBG official website icon](https://wstatic-prod.pubg.com/web/live/static/favicons/favicon-96x96.png).
