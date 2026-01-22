import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService, AuthUser } from '../auth/auth.service';
import { ChatMessage, ChatService, ReservationItem, SupportAgent, ThreadItem, TypingEvent } from './chat.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  // on declare les modules Angular utilises par ce composant
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  // on attache les styles du composant
  styleUrls: ['./chat.component.css']
})
export class ChatComponent implements OnInit, OnDestroy {
  // on stocke les donnees affichees dans l UI
  messages: ChatMessage[] = [];
  content = '';
  threads: ThreadItem[] = [];
  selectedThread?: ThreadItem;
  reservations: ReservationItem[] = [];
  supportAgents: SupportAgent[] = [];
  newTicketSubject = '';
  selectedReservationId = '';
  selectedTransferUserId = '';
  ticketFilter: 'ALL' | 'OPEN' | 'PENDING' | 'CLOSED' = 'ALL';
  typingLabel = '';
  user?: AuthUser | null;
  email = 'client@test.com';
  password = '123soleil';
  // on garde des subscriptions pour pouvoir se desabonner proprement
  private sub?: Subscription;
  private threadSub?: Subscription;
  private typingSub?: Subscription;
  private authSub?: Subscription;
  // on centralise les infos de frappe
  private typingUsers: Record<string, string> = {};
  private typingTimeoutId?: ReturnType<typeof setTimeout>;
  private typingThreadId?: string;

  constructor(private chat: ChatService, private auth: AuthService) {}

  ngOnInit(): void {
    // on s abonne aux flux de messages
    this.sub = this.chat.messages$.subscribe(m => (this.messages = m));
    // on s abonne aux mises a jour de tickets
    this.threadSub = this.chat.threadUpdates$.subscribe(update => {
      if (!update || !this.user) return;
      // on filtre si le ticket est assigne a un autre agent support
      if (this.user.role === 'SUPPORT' && update.assignedSupportUserId && update.assignedSupportUserId !== this.user.userId) {
        const hadThread = this.threads.some(t => t.id === update.id);
        if (hadThread) {
          this.threads = this.threads.filter(t => t.id !== update.id);
        }
        if (this.selectedThread?.id === update.id) {
          this.selectedThread = undefined;
          this.messages = [];
          this.clearTypingUsers();
          this.typingLabel = '';
        }
        return;
      }
      // on met a jour la liste des tickets
      const existing = this.threads.find(t => t.id === update.id);
      if (existing) {
        this.threads = this.threads.map(t => (t.id === update.id ? update : t));
      } else {
        this.threads = [update, ...this.threads];
      }
      if (this.selectedThread?.id === update.id) {
        this.selectedThread = update;
        if (update.status === 'CLOSED') {
          this.stopTyping();
          this.clearTypingUsers();
        }
      }
      this.syncSelectionWithFilter();
    });
    // on ecoute les evenements de frappe
    this.typingSub = this.chat.typing$.subscribe(event => this.onTypingEvent(event));
    // on reagit aux changements de connexion
    this.authSub = this.auth.user$.subscribe(user => {
      this.user = user;
      if (user) {
        // on parametre les topics websocket selon le role
        this.chat.setThreadUpdatesTopic(user.role, user.userId);
        this.afterLogin();
      } else {
        // on nettoie l interface quand on se deconnecte
        this.chat.disconnect();
        this.chat.setThreadUpdatesTopic('CLIENT');
        this.threads = [];
        this.messages = [];
        this.selectedThread = undefined;
        this.reservations = [];
        this.supportAgents = [];
        this.newTicketSubject = '';
        this.selectedReservationId = '';
        this.selectedTransferUserId = '';
        this.ticketFilter = 'ALL';
        this.clearTypingUsers();
        this.typingLabel = '';
        this.typingThreadId = undefined;
      }
    });
  }

  ngOnDestroy(): void {
    // on libere toutes les subscriptions et timers
    this.sub?.unsubscribe();
    this.threadSub?.unsubscribe();
    this.typingSub?.unsubscribe();
    this.authSub?.unsubscribe();
    if (this.typingTimeoutId) {
      clearTimeout(this.typingTimeoutId);
      this.typingTimeoutId = undefined;
    }
    this.chat.disconnect();
  }

  login(): void {
    // on valide et lance la connexion
    const email = this.email.trim();
    const password = this.password.trim();
    if (!email || !password) return;
    this.auth.login(email, password).subscribe({
      next: () => this.afterLogin(),
      error: () => alert('Connexion echouee')
    });
  }

  logout(): void {
    // on demande la deconnexion
    this.auth.logout();
  }

  send(): void {
    // on envoie un message si on a les droits
    if (!this.selectedThread || this.selectedThread.status === 'CLOSED') return;
    if (!this.canSend()) return;
    this.chat.send(this.content, this.selectedThread.id);
    this.content = '';
    this.stopTyping();
  }

  clear(): void {
    // on vide l historique local
    this.chat.clear();
  }

  selectThread(thread: ThreadItem): void {
    // on change de ticket selectionne
    if (this.selectedThread?.id && this.selectedThread.id !== thread.id) {
      this.stopTyping(this.selectedThread.id);
    }
    this.selectedThread = thread;
    this.chat.loadMessages(thread.id).subscribe();
    this.chat.subscribeToThread(thread.id);
    this.clearTypingUsers();
    this.selectedTransferUserId = '';
  }

  createTicket(): void {
    // on cree un ticket en tant que client
    if (!this.user || this.user.role !== 'CLIENT') return;
    const subject = this.newTicketSubject.trim();
    if (!subject) return;
    const reservationId = this.selectedReservationId.trim() || null;
    this.chat.createThread(subject, reservationId).subscribe({
      next: thread => {
        const existing = this.threads.find(t => t.id === thread.id);
        if (existing) {
          this.threads = this.threads.map(t => (t.id === thread.id ? thread : t));
        } else {
          this.threads = [thread, ...this.threads];
        }
        this.newTicketSubject = '';
        this.selectedReservationId = '';
        this.selectThread(thread);
      },
      error: () => alert('Creation du ticket echouee')
    });
  }

  senderLabel(message: ChatMessage): string {
    // on choisit le meilleur label possible pour l affichage
    return message.senderName || message.senderEmail || message.senderUserId;
  }

  closeTicket(): void {
    // on cloture un ticket en tant que support assigne
    if (!this.user || this.user.role !== 'SUPPORT' || !this.selectedThread) return;
    if (this.selectedThread.assignedSupportUserId !== this.user.userId) return;
    const threadId = this.selectedThread.id;
    this.chat.closeThread(threadId).subscribe({
      next: updated => {
        this.threads = this.threads.map(t => (t.id === updated.id ? updated : t));
        if (this.selectedThread?.id === updated.id) {
          this.selectedThread = updated;
        }
      },
      error: () => alert('Cloture du ticket echouee')
    });
  }

  claimTicket(thread: ThreadItem, event?: Event): void {
    // on accepte un ticket depuis la liste
    event?.stopPropagation();
    if (!this.user || this.user.role !== 'SUPPORT') return;
    this.chat.claimThread(thread.id).subscribe({
      next: updated => {
        const existing = this.threads.find(t => t.id === updated.id);
        if (existing) {
          this.threads = this.threads.map(t => (t.id === updated.id ? updated : t));
        } else {
          this.threads = [updated, ...this.threads];
        }
        if (this.selectedThread?.id === updated.id) {
          this.selectedThread = updated;
        }
      },
      error: () => alert('Acceptation du ticket echouee')
    });
  }

  transferTicket(): void {
    // on transfere un ticket vers un autre support
    if (!this.selectedThread || !this.selectedTransferUserId) return;
    if (!this.user || this.user.role !== 'SUPPORT') return;
    if (this.selectedThread.assignedSupportUserId !== this.user.userId) return;
    this.chat.transferThread(this.selectedThread.id, this.selectedTransferUserId).subscribe({
      next: updated => {
        this.threads = this.threads.map(t => (t.id === updated.id ? updated : t));
        if (this.selectedThread?.id === updated.id) {
          this.selectedThread = updated;
        }
        this.selectedTransferUserId = '';
      },
      error: () => alert('Transfert du ticket echoue')
    });
  }

  reservationById(reservationId?: string | null): ReservationItem | undefined {
    // on retrouve une reservation par son id
    if (!reservationId) return undefined;
    return this.reservations.find(reservation => reservation.id === reservationId);
  }

  reservationLabel(reservation: ReservationItem): string {
    // on mappe le code categorie vers un libelle simple
    switch (reservation.carCategoryCode) {
      case 'VAN6':
        return 'Voiture 6 places';
      case 'SED4':
        return 'Voiture 4 places';
      default:
        return reservation.carCategoryCode;
    }
  }

  openTicketsCount(): number {
    return this.threads.filter(t => t.status !== 'CLOSED' && !!t.assignedSupportUserId).length;
  }

  closedTicketsCount(): number {
    return this.threads.filter(t => t.status === 'CLOSED').length;
  }

  pendingTicketsCount(): number {
    return this.threads.filter(t => t.status !== 'CLOSED' && !t.assignedSupportUserId).length;
  }

  setTicketFilter(filter: 'OPEN' | 'PENDING' | 'CLOSED'): void {
    // on bascule l affichage de la liste
    this.ticketFilter = this.ticketFilter === filter ? 'ALL' : filter;
    this.syncSelectionWithFilter();
  }

  filteredThreads(): ThreadItem[] {
    // on applique un filtre local a la liste
    switch (this.ticketFilter) {
      case 'OPEN':
        return this.threads.filter(t => t.status !== 'CLOSED' && !!t.assignedSupportUserId);
      case 'PENDING':
        return this.threads.filter(t => t.status !== 'CLOSED' && !t.assignedSupportUserId);
      case 'CLOSED':
        return this.threads.filter(t => t.status === 'CLOSED');
      default:
        return this.threads;
    }
  }

  onTypingInput(): void {
    // on emet l etat de frappe
    if (!this.selectedThread || this.selectedThread.status === 'CLOSED') return;
    if (!this.canSend()) return;
    const threadId = this.selectedThread.id;
    if (this.typingThreadId !== threadId) {
      this.typingThreadId = threadId;
    }
    this.chat.sendTyping(threadId, true);
    if (this.typingTimeoutId) {
      clearTimeout(this.typingTimeoutId);
    }
    this.typingTimeoutId = setTimeout(() => this.stopTyping(threadId), 2000);
  }

  private stopTyping(threadId?: string): void {
    // on stoppe l etat de frappe apres un delai
    const id = threadId || this.typingThreadId;
    if (!id) return;
    this.chat.sendTyping(id, false);
    this.typingThreadId = undefined;
    if (this.typingTimeoutId) {
      clearTimeout(this.typingTimeoutId);
      this.typingTimeoutId = undefined;
    }
  }

  canSend(): boolean {
    // on verifie si l utilisateur peut envoyer dans ce ticket
    if (!this.selectedThread || this.selectedThread.status === 'CLOSED') return false;
    if (this.user?.role === 'SUPPORT') {
      return this.selectedThread.assignedSupportUserId === this.user.userId;
    }
    return true;
  }

  private onTypingEvent(event: TypingEvent | null): void {
    // on met a jour l indicateur "en train d ecrire"
    if (!event || !this.selectedThread || event.threadId !== this.selectedThread.id) return;
    if (event.senderUserId === this.user?.userId) return;
    const label = event.senderName || event.senderEmail || event.senderUserId;
    if (event.typing) {
      this.typingUsers[event.senderUserId] = label;
    } else {
      delete this.typingUsers[event.senderUserId];
    }
    this.updateTypingLabel();
  }

  private updateTypingLabel(): void {
    // on construit une phrase lisible pour l UI
    const names = Object.values(this.typingUsers);
    if (names.length === 0) {
      this.typingLabel = '';
      return;
    }
    if (names.length === 1) {
      this.typingLabel = `${names[0]} est en train d'ecrire...`;
      return;
    }
    this.typingLabel = `${names.join(', ')} sont en train d'ecrire...`;
  }

  private clearTypingUsers(): void {
    // on remet a zero les indicateurs de frappe
    this.typingUsers = {};
    this.typingLabel = '';
  }

  private afterLogin(): void {
    // on connecte la websocket et on charge les donnees
    this.chat.connect();
    this.chat.subscribeToThreadUpdates();
    this.chat.listThreads().subscribe({
      next: threads => {
        this.threads = threads;
        this.syncSelectionWithFilter();
        const filtered = this.filteredThreads();
        if (filtered.length > 0) {
          this.selectThread(filtered[0]);
        }
      },
      error: () => alert('Chargement des tickets echoue')
    });

    if (this.user?.role === 'CLIENT') {
      // on charge les reservations uniquement pour le client
      this.chat.listReservations().subscribe({
        next: reservations => (this.reservations = reservations),
        error: () => alert('Chargement des reservations echoue')
      });
    }

    if (this.user?.role === 'SUPPORT') {
      // on charge la liste des agents support pour les transferts
      this.chat.listSupportAgents().subscribe({
        next: agents => {
          this.supportAgents = this.user
            ? agents.filter(agent => agent.id !== this.user?.userId)
            : agents;
        },
        error: () => alert('Chargement des agents support echoue')
      });
    }
  }

  private syncSelectionWithFilter(): void {
    // on annule la selection si elle n apparait plus dans la liste filtre
    if (!this.selectedThread) return;
    if (this.filteredThreads().some(t => t.id === this.selectedThread?.id)) return;
    this.selectedThread = undefined;
    this.messages = [];
    this.clearTypingUsers();
    this.typingLabel = '';
  }
}
