import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { ToastProvider } from './components/ui';
import { AuthProvider } from './state/AuthContext';
import { MatchProvider } from './state/MatchContext';
import { PartySessionProvider } from './state/PartySessionContext';
import { SocialProvider } from './state/SocialContext';
import './styles/theme.css';
import './styles/pages.css';
import './styles/usability.css';
import './styles/match-composer.css';
import './styles/home.css';
import './styles/recruitment.css';
import './styles/sidebar.css';
import './styles/profile.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <MatchProvider>
            <SocialProvider>
              <PartySessionProvider><App /></PartySessionProvider>
            </SocialProvider>
          </MatchProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
