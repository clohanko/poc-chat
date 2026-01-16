import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ChatComponent } from './chat.component';

// on verifie que le composant de chat se cree correctement
describe('ChatComponent', () => {
  let component: ChatComponent;
  let fixture: ComponentFixture<ChatComponent>;

  beforeEach(async () => {
    // on configure le module de test
    await TestBed.configureTestingModule({
      imports: [ChatComponent, HttpClientTestingModule]
    })
    .compileComponents();

    // on instancie le composant et on declenche le rendu initial
    fixture = TestBed.createComponent(ChatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    // on attend que le composant existe
    expect(component).toBeTruthy();
  });
});
