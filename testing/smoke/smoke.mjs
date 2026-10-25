// Smoke test of the web app on a new unit: the initial setup, creating a composition and saving the settings,
// like a user does after unpacking a unit. Run it against a unit started from the built jar:
//
//   BASE_URL=http://localhost:8080 node smoke.mjs
//
// It fails on any error of the web app (uncaught exceptions and failed API calls) and leaves screenshots of
// every step in SCREENSHOT_DIRECTORY.

import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const baseUrl = process.env.BASE_URL || 'http://localhost:8080';
const screenshotDirectory = process.env.SCREENSHOT_DIRECTORY || 'screenshots';
const adminPassword = 'smoke-test-password';
const deviceName = 'Smoke test unit';
const renamedDeviceName = 'Renamed smoke test unit';
const compositionName = 'Smoke test composition';

mkdirSync(screenshotDirectory, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage();
const errors = [];
let stepNumber = 0;

page.on('pageerror', (error) => errors.push(`Uncaught error: ${error.message}`));
page.on('response', (response) => {
  if (response.url().includes('/api/') && response.status() >= 500) {
    errors.push(`${response.request().method()} ${response.url()} answered ${response.status()}`);
  }
});

// The device name in the system settings
function deviceNameInput() {
  return page.getByPlaceholder('My Device');
}

async function step(name, action) {
  stepNumber++;
  console.log(`${stepNumber}. ${name}`);

  try {
    await action();
  } finally {
    await page.screenshot({ path: join(screenshotDirectory, `${String(stepNumber).padStart(2, '0')} ${name}.png`), fullPage: true });
  }

  if (errors.length > 0) {
    throw new Error(`The web app failed while "${name}":\n${errors.join('\n')}`);
  }
}

try {
  await step('Open the new unit', async () => {
    await page.goto(baseUrl);
    await page.waitForURL(/#\/intro/);
  });

  await step('Set up the unit', async () => {
    await page.getByRole('button', { name: 'Next' }).click();
    await page.locator('#deviceNameInput').fill(deviceName);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.locator('input[name="password1"]').fill(adminPassword);
    await page.locator('input[name="password2"]').fill(adminPassword);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.locator('input[name="wifiApDeactivated"]').check({ force: true });
    await page.getByRole('button', { name: 'Finish' }).click();
    await page.waitForURL(/#\/play/);
  });

  await step('Create a composition', async () => {
    await page.goto(`${baseUrl}/#/editor`);
    await page.getByRole('button', { name: 'Create composition' }).click();
    await page.locator('#compositionName').fill(compositionName);
    await page.getByRole('button', { name: 'Save' }).click();
    await page.getByText('The composition has been saved.').first().waitFor();
  });

  await step('Find the composition after a reload', async () => {
    await page.reload();
    await page.getByText(compositionName).first().waitFor();
  });

  await step('Rename the unit in the settings', async () => {
    await page.goto(`${baseUrl}/#/settings`);
    await deviceNameInput().fill(renamedDeviceName);
    await page.getByRole('button', { name: 'Save' }).click();
    await page.getByText('The settings have been saved.').first().waitFor();
  });

  await step('Find the new name after a reload', async () => {
    await page.reload();
    await page.getByText(renamedDeviceName).first().waitFor();

    const value = await deviceNameInput().inputValue();
    if (value !== renamedDeviceName) {
      throw new Error(`The settings show the device name "${value}" instead of "${renamedDeviceName}"`);
    }
  });

  console.log('The web app works');
} finally {
  await browser.close();
}
