import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import './index.css';

const storedTheme = localStorage.getItem('theme');
if (storedTheme === 'dark') {
  document.documentElement.classList.add('dark');
}

createApp(App).use(router).mount('#app');
