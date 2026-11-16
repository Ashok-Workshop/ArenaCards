# ArenaCards master roadmap

Updated: 2026-11-14

Status terms: **Not started** = no implementation; **In progress** = code/doc work underway; **Implemented** = source exists but test status is separate; **Unit tested** = targeted automated tests pass; **Integration tested** = Firebase/emulator/device boundary tested; **Deployment pending** = requires owner-approved deployment/configuration; **Production verified** = verified with production services/real devices.

| Priority | Workstream | Current status | Dependencies / exit criteria |
|---|---|---|---|
| P0 | Offline classic-style rules | Unit tested | Wild Draw Four challenge and last-card edge tests pass; stacking disabled. Verify any future rule changes with regressions. |
| P0 | Offline quality and accessibility | Locally implemented; automated checks pass | Persistent sound/haptic/reduced-motion/theme settings, local practice history, original Compose graphics, and accessible card feedback are implemented. Compose UI/device accessibility QA remains pending; owner device testing is reserved for final QA. |
| P1 | Auth/profile deployment readiness | Source implemented; integration pending | Confirm project/provider/region, App Check and quotas, review rules/functions; emulator and test-account validation; deployment requires owner approval. |
| P1 | Account privacy and deletion | Not started | Define deletion/retention policy and trusted deletion flow; test Auth/Firestore cleanup. |
| P2 | Wallet, missions and rewards | Emulator integration tested; deployment pending | Wallet ledger, daily/missions/XP and idempotent claims are in source with Android UI. Online match-end rewards are implemented separately; online-match mission progress and coin spending remain. Never reward offline practice. |
| P2 | Private online rooms / authoritative matches | Emulator integration tested; deployment pending | Human-only matchmaking, private room codes, server-authoritative gameplay, reconnect/timeouts, verified results/history, and atomic rewards are in source with Android UI. Load/scale, production, and physical-device tests remain. |
| P3 | Friends, blocks and reports | Integration tested; deployment pending | Source, Android UI, authorization, privacy, and abuse/rate-limit tests exist. Production moderation/retention policy and physical-device QA remain. |
| P3 | Chat and moderation | Integration tested; deployment pending | Source, Android UI, membership/block authorization, reporting, and tests exist. Production retention, human moderation operations, and device QA remain. |
| P3 | Leaderboards, seasons, tournaments and progression | Integration tested; deployment pending | Verified-match ratings, friend/global/season rankings, owner-managed season lifecycle, single-elimination registration/brackets/rewards, and Android entry UI are implemented. Production remains disabled; physical-device/privacy QA and owner-approved operational setup remain. |
| P3 | Notifications | Integration tested; deployment pending | Event backends, preferences, Android permission flow, and emulator tests exist. Live delivery and device behavior remain unverified. |
| P4 | Admin/moderation console | Not started | Server-enforced roles, audit trail and incident process; no client admin flags. |
| P4 | Ads and Play release | Not started | Consent/privacy/policy review, test-only ads, server-side verification, release signing and owner approval. |

No phase is production-ready solely because source compiles. Firebase deployment, billing activation, production data changes, ad publication, and Play publication are separate owner-approved actions.

## Current recommended sequence

1. Finish backend/profile readiness, App Check plan, privacy/account deletion, monitoring, and test-project integration checks.
2. Complete owner policy and operational setup for moderation, account deletion/retention, App Check, monitoring, and trusted admin claims.
3. Finish offline/online/competitive physical-device and accessibility QA after the planned feature phases.
4. Complete admin, security, cost, and Google Play readiness; deploy/publish only after explicit approvals.
5. Keep AdMob SSV, coin spending, and full administrative tooling as separate future work.
