import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

// on declare les routes de l application
const routes: Routes = [];

@NgModule({
  // on configure le router avec la liste de routes
  imports: [RouterModule.forRoot(routes)],
  // on exporte le router pour l utiliser dans l app
  exports: [RouterModule]
})
// on expose un module de routing propre a l app
export class AppRoutingModule { }
