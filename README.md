# ArcTracker

ArcTracker is now structured like a production-ready Expo / React Native codebase: typed routing, shared design tokens, reusable UI primitives, and explicit quality gates.

## Engineering standards

- **Expo Router as the application shell** for typed, scalable screen composition.
- **Semantic design tokens** in `constants/theme.ts` to keep spacing, color, and typography decisions centralized.
- **Reusable themed primitives** in `components/` so product screens stay focused on behavior instead of repeated styling glue.
- **Strict TypeScript + ESLint** as the default quality gates for every change.

## Project structure

```text
app/                 File-based routes and screen entrypoints
components/          Shared themed UI primitives
components/ui/       Layout-focused building blocks
constants/           Design tokens and app-wide constants
hooks/               Theme and platform-aware hooks
scripts/             Project automation
assets/              Images and other static assets
```

## Scripts

```bash
npm run start        # Start Expo
npm run android      # Start Android target
npm run ios          # Start iOS target
npm run web          # Start the web target
npm run lint         # Run Expo / ESLint checks
npm run lint:fix     # Auto-fix lint issues when possible
npm run typecheck    # Run TypeScript without emitting output
```

## Working agreements for future features

1. Add new routes in `app/`, but move reusable view logic into `components/`.
2. Prefer semantic colors from `useThemeColor()` or shared tokens over hard-coded hex values.
3. Keep screens declarative: fetch / derive data above the render tree, and compose with small presentational units.
4. Run lint and typecheck before committing.

## Getting started

```bash
npm install
npm run start
```
