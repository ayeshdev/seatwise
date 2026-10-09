/** @type {import('tailwindcss').Config} */
const token = (name) => `rgb(var(--${name}) / <alpha-value>)`;

module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      // Colour values live in src/styles/tokens.css (light + dark). Never use raw hex in components.
      colors: {
        canvas: token('canvas'),
        surface: token('surface'),
        'surface-muted': token('surface-muted'),
        ink: token('ink'),
        'ink-muted': token('ink-muted'),
        line: token('line'),
        accent: token('accent'),
        'accent-soft': token('accent-soft'),
        ok: token('ok'),
        warn: token('warn'),
        full: token('full'),
        quiet: token('quiet'),
      },
      fontFamily: {
        serif: ['"Source Serif 4"', 'Georgia', 'Cambria', 'serif'],
        sans: [
          'Inter',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
      borderRadius: {
        card: 'var(--radius-card)',
        button: 'var(--radius-button)',
      },
    },
  },
  plugins: [require('@tailwindcss/forms')],
};
