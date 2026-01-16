import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/auth/auth.interceptor';

// on demarre l application Angular avec le composant racine
bootstrapApplication(AppComponent, {
  // on declare ici les providers globaux (ex: HTTP)
  providers: [provideHttpClient(withInterceptors([authInterceptor]))]
  // on peut ajouter d autres providers plus tard si besoin
}).catch(err => console.error(err));
