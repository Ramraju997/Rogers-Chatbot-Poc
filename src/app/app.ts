import { Component, ElementRef, inject, signal, viewChild, afterRenderEffect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatService } from './chat.service';

interface Message {
  from: 'user' | 'bot';
  text: string;
  error?: boolean;
}

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly chat = inject(ChatService);

  readonly messages = signal<Message[]>([
    { from: 'bot', text: "Hi! I'm the Rogers assistant. Ask me about phones and plans." },
  ]);
  readonly sending = signal(false);
  readonly suggestions = ['hi', 'I need iPhone 18 with unlimited calling'];

  draft = '';

  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  constructor() {
    // Keep the newest message in view.
    afterRenderEffect(() => {
      this.messages();
      this.sending();
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  send(text: string = this.draft): void {
    const message = text.trim();
    if (!message || this.sending()) return;

    this.messages.update((m) => [...m, { from: 'user', text: message }]);
    this.draft = '';
    this.sending.set(true);

    this.chat.send(message).subscribe({
      next: (reply) => {
        const texts = reply.messages.length ? reply.messages : ['(No response from agent)'];
        this.messages.update((m) => [...m, ...texts.map((t) => ({ from: 'bot' as const, text: t }))]);
        this.sending.set(false);
      },
      error: (err) => {
        const detail = err?.error?.error ?? 'Could not reach the server. Is it running on port 3000?';
        this.messages.update((m) => [...m, { from: 'bot', text: detail, error: true }]);
        this.sending.set(false);
      },
    });
  }
}
