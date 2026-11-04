/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      fontFamily: { sans: ['Nunito', 'system-ui', 'sans-serif'] },
      colors: { brand: { 50: '#faf5ff', 100: '#f3e8ff', 200: '#e9d5ff', 400: '#a78bfa', 500: '#8b5cf6', 600: '#7c3aed', 700: '#6d28d9', 900: '#3b0764' } },
      keyframes: {
        pop: { '0%': { transform: 'scale(.8)', opacity: '0' }, '100%': { transform: 'scale(1)', opacity: '1' } },
        float: { '0%,100%': { transform: 'translateY(0)' }, '50%': { transform: 'translateY(-6px)' } },
        fall: { '0%': { transform: 'translateY(-10vh) rotate(0)', opacity: '1' }, '100%': { transform: 'translateY(110vh) rotate(720deg)', opacity: '0' } },
        shake: { '0%,100%': { transform: 'translateX(0)' }, '25%': { transform: 'translateX(-6px)' }, '75%': { transform: 'translateX(6px)' } },
      },
      animation: { pop: 'pop .25s ease-out', float: 'float 3s ease-in-out infinite', fall: 'fall 2.5s ease-in forwards', shake: 'shake .3s' },
    },
  },
  plugins: [],
};
