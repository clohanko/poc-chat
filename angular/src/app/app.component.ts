import { Component } from '@angular/core';
import { ChatComponent } from './chat/chat.component';

@Component({
  selector: 'app-root',
  standalone: true,
  // on declare les composants utilises par ce composant racine
  imports: [ChatComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
// on garde ce composant racine minimal: il delegue au chat
export class AppComponent {}
