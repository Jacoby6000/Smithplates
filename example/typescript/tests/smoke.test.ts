import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

describe('smoke', () => {
  it('loads generated client module', async () => {
    const mod = await import('../src/generated/petstore/api/clients/petsClient.js');
    assert.ok(mod.PetsApiClient, 'PetsApiClient should be exported');
  });
});
