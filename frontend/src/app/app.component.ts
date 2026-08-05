import { Component, VERSION } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'bna-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet></router-outlet>`,
  styles: [`:host { display: block; min-height: 100vh; background: var(--color-bg-primary, #0d1b2a); }`]
})
export class AppComponent {
  version = VERSION.full;
}
