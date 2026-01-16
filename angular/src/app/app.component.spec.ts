import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { AppComponent } from './app.component';

// on verifie que le composant racine se cree correctement
describe('AppComponent', () => {
  beforeEach(async () => {
    // on configure le module de test
    await TestBed.configureTestingModule({
      imports: [AppComponent, HttpClientTestingModule]
    }).compileComponents();
  });

  it('should create the app', () => {
    // on verifie que l instance existe
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
