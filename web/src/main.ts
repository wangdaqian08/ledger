import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { i18n } from './i18n'

// Tokens first: every component below reads its colours and spacing from these custom properties.
import './tokens/fonts.css'
import './tokens/colors.css'
import './tokens/spacing.css'
import './tokens/typography.css'
import './tokens/radius.css'
import './tokens/elevation.css'
import './tokens/motion.css'
import './tokens/base.css'

createApp(App).use(createPinia()).use(i18n).mount('#app')
