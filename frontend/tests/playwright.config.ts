import { defineConfig } from '@playwright/test';

// Pure room-selection tests; no browser or preview server is started.
export default defineConfig({ testDir: '.', testMatch: 'quick-connect.spec.ts', reporter: 'list' });
