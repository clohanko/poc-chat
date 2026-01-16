import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthUser {
  token: string;
  userId: string;
  email: string;
  role: 'CLIENT' | 'SUPPORT';
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  // on utilise une cle fixe pour stocker l utilisateur en local
  private readonly storageKey = 'poc-chat-auth';
  // on expose l utilisateur courant via un BehaviorSubject
  private userSubject = new BehaviorSubject<AuthUser | null>(this.load());
  user$ = this.userSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(email: string, password: string) {
    // on appelle l API pour se connecter et recuperer le token
    return this.http.post<AuthUser>(`${environment.apiBase}/auth/login`, { email, password })
      .pipe(
        tap(user => {
          // on persiste l utilisateur dans le localStorage pour survivre au refresh
          localStorage.setItem(this.storageKey, JSON.stringify(user));
          // on notifie tous les abonnements de la nouvelle connexion
          this.userSubject.next(user);
        })
      );
  }

  logout(): void {
    // on nettoie le stockage et on repasse a null
    localStorage.removeItem(this.storageKey);
    this.userSubject.next(null);
  }

  token(): string | null {
    // on expose le token pour les appels API
    return this.userSubject.value?.token ?? null;
  }

  currentUser(): AuthUser | null {
    // on retourne l utilisateur courant sans souscrire
    return this.userSubject.value;
  }

  private load(): AuthUser | null {
    // on tente de relire la session depuis le localStorage
    const raw = localStorage.getItem(this.storageKey);
    if (!raw) return null;
    try {
      // on parse en JSON avec un fallback safe
      return JSON.parse(raw) as AuthUser;
    } catch {
      // on retourne null si le JSON est invalide
      return null;
    }
  }
}
