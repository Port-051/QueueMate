import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { ToastProvider } from './components/ui';
import { AuthProvider } from './state/AuthContext';
import { MatchProvider } from './state/MatchContext';
import { SocialProvider } from './state/SocialContext';
import './styles/theme.css';
import './styles/pages.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <MatchProvider>
            <SocialProvider>
              <App />
            </SocialProvider>
          </MatchProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
