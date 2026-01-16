import { Injectable } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../environments/environment';

export interface ChatMessage {
  // on decrit le message affiche dans le chat
  content: string;
  sentAt: string;
  threadId: string;
  senderUserId: string;
  senderName?: string;
  senderEmail?: string;
}

export interface ThreadItem {
  // on decrit un ticket visible dans la liste
  id: string;
  subject: string;
  status: string;
  createdAt: string;
  createdByUserId: string;
  createdByName?: string;
  createdByEmail?: string;
  reservationId?: string | null;
  assignedSupportUserId?: string | null;
  assignedSupportName?: string | null;
  assignedSupportEmail?: string | null;
}

export interface ReservationItem {
  // on decrit une reservation pour lier un ticket a une location
  id: string;
  startAt: string;
  endAt: string;
  status: string;
  totalPriceCents: number;
  currency: string;
  carCategoryCode: string;
}

export interface TypingEvent {
  // on decrit les evenements de frappe en temps reel
  threadId: string;
  senderUserId: string;
  senderName?: string;
  senderEmail?: string;
  typing: boolean;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  // on garde un client STOMP pour la websocket
  private client?: Client;
  private subscription?: StompSubscription;
  private threadSubscription?: StompSubscription;
  private typingSubscription?: StompSubscription;
  private activeThreadId?: string;
  private wantThreadUpdates = false;
  private threadUpdatesTopic?: string;
  // on centralise la base URL pour l API REST
  private readonly apiBase = environment.apiBase;

  // on expose les flux de messages et de mises a jour
  private messagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  messages$ = this.messagesSubject.asObservable();
  private threadUpdatesSubject = new BehaviorSubject<ThreadItem | null>(null);
  threadUpdates$ = this.threadUpdatesSubject.asObservable();
  private typingSubject = new BehaviorSubject<TypingEvent | null>(null);
  typing$ = this.typingSubject.asObservable();

  constructor(private http: HttpClient, private auth: AuthService) {}

  connect(): void {
    // on evite de reconnecter si deja actif
    if (this.client?.active) return;
    const token = this.auth.token();
    if (!token) return;
    // on configure le client STOMP avec le token
    this.client = new Client({
      brokerURL: this.resolveWsUrl(),
      reconnectDelay: 3000,
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        // on reprend les abonnements si besoin
        if (this.activeThreadId) {
          this.subscribeToThread(this.activeThreadId);
        }
        if (this.wantThreadUpdates) {
          this.subscribeToThreadUpdates();
        }
      },
      onStompError: () => {
        // on coupe proprement en cas d erreur websocket
        this.disconnect();
      }
    });

    // on lance la connexion websocket
    this.client.activate();
  }

  disconnect(): void {
    // on ferme les abonnements et on nettoie l etat
    this.client?.deactivate();
    this.subscription?.unsubscribe();
    this.subscription = undefined;
    this.threadSubscription?.unsubscribe();
    this.threadSubscription = undefined;
    this.typingSubscription?.unsubscribe();
    this.typingSubscription = undefined;
    this.wantThreadUpdates = false;
    this.threadUpdatesTopic = undefined;
  }

  subscribeToThread(threadId: string): void {
    // on s abonne aux messages d un ticket
    this.activeThreadId = threadId;
    if (!this.client?.connected) return;
    this.subscription?.unsubscribe();
    this.messagesSubject.next([]);
    this.subscription = this.client.subscribe(`/topic/threads/${threadId}`, (msg: IMessage) => {
      const body = JSON.parse(msg.body) as ChatMessage;
      this.messagesSubject.next([...this.messagesSubject.value, body]);
    });
    this.subscribeToTyping(threadId);
  }

  subscribeToThreadUpdates(): void {
    // on s abonne aux mises a jour de tickets
    this.wantThreadUpdates = true;
    if (!this.client?.connected) return;
    if (!this.threadUpdatesTopic) return;
    this.threadSubscription?.unsubscribe();
    this.threadSubscription = this.client.subscribe(this.threadUpdatesTopic, (msg: IMessage) => {
      const body = JSON.parse(msg.body) as ThreadItem;
      this.threadUpdatesSubject.next(body);
    });
  }

  setThreadUpdatesTopic(role: 'CLIENT' | 'SUPPORT', userId?: string): void {
    // on choisit le bon topic selon le role
    if (role === 'SUPPORT') {
      this.threadUpdatesTopic = '/topic/threads';
    } else if (role === 'CLIENT' && userId) {
      this.threadUpdatesTopic = `/topic/users/${userId}/threads`;
    } else {
      this.threadUpdatesTopic = undefined;
    }
  }

  listThreads(): Observable<ThreadItem[]> {
    // on charge la liste des tickets
    return this.http.get<ThreadItem[]>(`${this.apiBase}/threads`);
  }

  listReservations(): Observable<ReservationItem[]> {
    // on charge les reservations du client
    return this.http.get<ReservationItem[]>(`${this.apiBase}/reservations`);
  }

  createThread(subject: string, reservationId?: string | null): Observable<ThreadItem> {
    // on cree un nouveau ticket cote API
    const body = reservationId ? { subject, reservationId } : { subject };
    return this.http.post<ThreadItem>(`${this.apiBase}/threads`, body);
  }

  closeThread(threadId: string): Observable<ThreadItem> {
    // on cloture un ticket en tant que support
    return this.http.post<ThreadItem>(`${this.apiBase}/threads/${threadId}/close`, {});
  }

  claimThread(threadId: string): Observable<ThreadItem> {
    // on accepte un ticket en tant que support
    return this.http.post<ThreadItem>(`${this.apiBase}/threads/${threadId}/claim`, {});
  }

  loadMessages(threadId: string): Observable<ChatMessage[]> {
    // on charge l historique des messages
    return this.http.get<ChatMessage[]>(`${this.apiBase}/threads/${threadId}/messages`)
      .pipe(tap(messages => this.messagesSubject.next(messages)));
  }

  send(content: string, threadId: string): void {
    // on envoie un message via websocket
    const text = content.trim();
    if (!text || !this.client?.connected) return;

    this.client.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ content: text, threadId })
    });
  }

  sendTyping(threadId: string, typing: boolean): void {
    // on emet un evenement de frappe
    if (!this.client?.connected) return;
    this.client.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({ threadId, typing })
    });
  }

  clear(): void {
    // on vide les messages locaux
    this.messagesSubject.next([]);
  }

  private subscribeToTyping(threadId: string): void {
    // on s abonne aux evenements de frappe
    if (!this.client?.connected) return;
    this.typingSubscription?.unsubscribe();
    this.typingSubscription = this.client.subscribe(`/topic/threads/${threadId}/typing`, (msg: IMessage) => {
      const body = JSON.parse(msg.body) as TypingEvent;
      this.typingSubject.next(body);
    });
  }

  private resolveWsUrl(): string {
    // on supporte les urls absolues ou relatives selon l environnement
    const wsBase = environment.wsBase;
    if (wsBase.startsWith('ws://') || wsBase.startsWith('wss://')) {
      return wsBase;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const path = wsBase.startsWith('/') ? wsBase : `/${wsBase}`;
    return `${protocol}://${window.location.host}${path}`;
  }

  // on laisse l interceptor gerer les headers d auth
}
