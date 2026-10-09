/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/src/setup-jest.ts'],
  testEnvironment: 'jsdom',
  testRegex: '[\\\\/]src[\\\\/].*\\.spec\\.ts$',
  testPathIgnorePatterns: ['[\\\\/]node_modules[\\\\/]', '[\\\\/]e2e[\\\\/]', '[\\\\/]dist[\\\\/]'],
  moduleNameMapper: {
    '^@core/(.*)$': '<rootDir>/src/app/core/$1',
    '^@shared/(.*)$': '<rootDir>/src/app/shared/$1',
    '^@features/(.*)$': '<rootDir>/src/app/features/$1',
  },
  transform: {
    '^.+\\.(ts|js|mjs|html|svg)$': [
      'jest-preset-angular',
      { tsconfig: '<rootDir>/tsconfig.spec.json', stringifyContentPathRegex: '\\.(html|svg)$' },
    ],
  },
  // pnpm nests packages under node_modules/.pnpm/<pkg>@<ver>/node_modules/<pkg>, so match anywhere ahead.
  transformIgnorePatterns: ['node_modules/(?!.*(\\.mjs$|keycloak-js|keycloak-angular))'],
  collectCoverageFrom: ['src/app/**/*.ts', '!src/app/**/*.spec.ts'],
};
