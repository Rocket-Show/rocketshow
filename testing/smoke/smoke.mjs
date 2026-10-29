// Smoke test of the web app on a new unit: the initial setup, uploading media, creating a composition and a set,
// opening the designer, saving the settings and running the self-test, like a user does after unpacking a unit. Run
// it against a unit started from the built jar:
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
// Names with characters, which have to be encoded in a URL
const compositionName = 'Smoke test & composition #1 + 50%';
const renamedCompositionName = 'Renamed smoke test & composition #1 + 50%';
const setName = 'Smoke test set & more #2';
const mediaFileName = 'smoke test click #1 + 2.wav';

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

// A tenth of a second of silence
function wavFile() {
  const samples = 4410;
  const buffer = Buffer.alloc(44 + samples * 2);
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + samples * 2, 4);
  buffer.write('WAVE', 8);
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(44100, 24);
  buffer.writeUInt32LE(88200, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(samples * 2, 40);
  return buffer;
}

// Wait until the check is true
async function waitUntil(check, description) {
  for (let i = 0; i < 100; i++) {
    if (await check()) {
      return;
    }
    await page.waitForTimeout(100);
  }

  throw new Error(`Timed out waiting for ${description}`);
}

// The state of the unit, as the web app gets it
async function state() {
  const response = await page.request.get(`${baseUrl}/api/system/state`);
  return response.json();
}

// Wait for the message of a successful action. An error message fails the test right away.
async function expectMessage(text) {
  const message = page.getByText(text).first();
  const error = page.locator('.toast-error').first();
  await message.or(error).first().waitFor();

  if (await error.isVisible()) {
    throw new Error(`The web app showed the error "${await error.innerText()}" instead of "${text}"`);
  }
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
  });

  await step('Refuse a short password of the wireless access point', async () => {
    // A passphrase with less than 8 characters leaves the access point open
    await page.locator('input[name="wifiApEnabled"]').check({ force: true });
    await page.locator('#wifiApSame').uncheck();
    await page.locator('input[name="password1"]:visible').fill('short12');
    await page.locator('input[name="password2"]:visible').fill('short12');
    await page.getByText('needs 8 to 63 characters').waitFor();
    if (await page.getByRole('button', { name: 'Finish' }).isEnabled()) {
      throw new Error('The setup can be finished with an access point passphrase of 7 characters');
    }
  });

  await step('Finish the setup', async () => {
    await page.locator('input[name="wifiApDeactivated"]').check({ force: true });
    await page.getByRole('button', { name: 'Finish' }).click();
    await page.waitForURL(/#\/play/);
  });

  await step('Upload a media file', async () => {
    await page.goto(`${baseUrl}/#/editor`);
    await page.locator('#media-tab').click();
    await page.locator('input.dz-hidden-input').setInputFiles({ name: mediaFileName, mimeType: 'audio/wav', buffer: wavFile() });
    await page.locator('#media').getByText(mediaFileName).waitFor();
  });

  await step('Create a composition', async () => {
    await page.locator('#compositions-tab').click();
    await page.getByRole('button', { name: 'Create composition' }).click();
    await page.locator('#compositionName').fill(compositionName);
    await page.getByRole('button', { name: 'Save' }).click();
    await expectMessage('The composition has been saved.');
  });

  await step('Find the composition after a reload', async () => {
    await page.reload();
    await page.getByText(compositionName).first().waitFor();
  });

  await step('Open the composition', async () => {
    await page.locator('#compositions .list-group-item', { hasText: compositionName }).first().click();
    const name = page.locator('#compositionName');
    await waitUntil(async () => (await name.inputValue()) === compositionName, `the composition "${compositionName}" to open`);
  });

  await step('Create a set with the composition', async () => {
    await page.locator('#sets-tab').click();
    await page.getByRole('button', { name: 'Create set' }).click();
    await page.locator('#setName').fill(setName);
    // The arrow next to the composition in the available compositions
    await page.locator('#sets .list-group-item', { hasText: compositionName }).first().getByRole('button').click();
    await page.locator('#sets .list-sort-handle').first().waitFor();
    await page.locator('#sets').getByRole('button', { name: 'Save' }).click();
    await expectMessage('The set has been saved.');
  });

  await step('Find the set after a reload', async () => {
    await page.reload();
    await page.locator('#sets-tab').click();
    await page.locator('#sets .list-group-item', { hasText: setName }).first().click();
    const name = page.locator('#setName');
    await waitUntil(async () => (await name.inputValue()) === setName, `the set "${setName}" to open`);
  });

  await step('Rename the composition of the set', async () => {
    await page.locator('#compositions-tab').click();
    await page.locator('#compositions .list-group-item', { hasText: compositionName }).first().click();
    const name = page.locator('#compositionName');
    await waitUntil(async () => (await name.inputValue()) === compositionName, `the composition "${compositionName}" to open`);
    await name.fill(renamedCompositionName);
    await page.getByRole('button', { name: 'Save' }).first().click();
    await expectMessage('The composition has been saved.');

    // The set holds it under its new name, also after a reload
    await page.reload();
    await page.locator('#sets-tab').click();
    await page.locator('#sets .list-group-item', { hasText: setName }).first().click();
    await page.locator('#sets .list-group-item', { has: page.locator('.list-sort-handle'), hasText: renamedCompositionName }).first().waitFor();
  });

  await step('Delete the media file', async () => {
    await page.locator('#media-tab').click();
    await page.locator('#media .list-group-item', { hasText: mediaFileName }).locator('.text-danger').click();
    await page.getByRole('button', { name: 'OK' }).click();
    await waitUntil(async () => (await page.locator('#media .list-group-item', { hasText: mediaFileName }).count()) === 0, `the media file "${mediaFileName}" to disappear`);

    await page.reload();
    await page.locator('#media-tab').click();
    await page.locator('#media').getByText('GB').first().waitFor();
    if ((await page.locator('#media .list-group-item', { hasText: mediaFileName }).count()) > 0) {
      throw new Error(`The media file "${mediaFileName}" is still there`);
    }
  });

  await step('Select the composition to play', async () => {
    await page.goto(`${baseUrl}/#/play`);
    await page.locator('.list-group-item:visible', { hasText: renamedCompositionName }).first().click();
    await waitUntil(async () => (await state()).currentCompositionName === renamedCompositionName, `the composition "${renamedCompositionName}" to be selected`);
  });

  await step('Open the designer', async () => {
    await page.goto(`${baseUrl}/#/designer`);
    await page.locator('lib-designer').waitFor();
    // Give the designer time to load its project and the fixture library
    await page.waitForTimeout(3000);

    // A new project starts with the designer template, which is in the version of the designer
    if (await page.getByText('The project has been upgraded').isVisible()) {
      throw new Error('The designer migrated the designer template: raise dist/designer_template.json to its version');
    }
  });

  await step('Rename the unit in the settings', async () => {
    await page.goto(`${baseUrl}/#/settings`);
    await deviceNameInput().fill(renamedDeviceName);
    await page.getByRole('button', { name: 'Save' }).click();
    await expectMessage('The settings have been saved.');
  });

  await step('Find the new name after a reload', async () => {
    await page.reload();
    await page.getByText(renamedDeviceName).first().waitFor();

    const value = await deviceNameInput().inputValue();
    if (value !== renamedDeviceName) {
      throw new Error(`The settings show the device name "${value}" instead of "${renamedDeviceName}"`);
    }
  });

  await step('Run the self-test in the advanced settings', async () => {
    await page.locator('a.nav-link[href="#advanced"]').click();
    await page.locator('#runSelfTest').click();
    // After the warning about the test tones and the outputs it switches
    await page.locator('app-warning-dialog').getByRole('button', { name: 'OK' }).click();
    // In a dialog of its own, shown once the wait dialog has faded out
    await page.locator('modal-container.show app-self-test-result-dialog #selfTestResult').waitFor({ timeout: 60000 });
    await page.locator('app-wait-dialog').waitFor({ state: 'detached' });

    // Some checks depend on the machine running the unit (e.g. its clock, network or kernel log), so a check may fail
    const result = await page.locator('#selfTestResult').innerText();
    console.log(result);
    if (!/^(Passed|Failed)/.test(result) || await page.locator('#selfTestResult tbody tr').count() < 20) {
      throw new Error(`The self-test of the new unit shows no result for every check:\n${result}`);
    }
  });

  await step('Close the result of the self-test', async () => {
    await page.locator('#closeSelfTestResult').click();
    await page.locator('app-self-test-result-dialog').waitFor({ state: 'detached' });
    await page.locator('#runSelfTest').waitFor();
  });

  console.log('The web app works');
} finally {
  await browser.close();
}
