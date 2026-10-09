import { apiBearerCondition } from './auth.providers';

describe('apiBearerCondition', () => {
  it('matches only our own API', () => {
    const { urlPattern } = apiBearerCondition('/api');

    expect(urlPattern.test('/api/v1/me')).toBe(true);
    expect(urlPattern.test('/config.json')).toBe(false);
    expect(urlPattern.test('https://elsewhere.example/api/v1/me')).toBe(false);
  });

  it('copes with a trailing slash and regex characters in the base', () => {
    expect(apiBearerCondition('/api/').urlPattern.test('/api/v1/me')).toBe(true);
    expect(apiBearerCondition('https://a.b/api').urlPattern.test('https://aXb/api/v1')).toBe(false);
  });
});
