import { Component, ElementRef, inject, signal, viewChild, afterRenderEffect } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, PaymentQuote, RecommendResult } from './chat.service';

interface Message {
  from: 'user' | 'bot';
  text: string;
  error?: boolean;
  /** Device + plan options shown as cards under this message. */
  result?: RecommendResult;
}

const PAYMENT_NAMES: Record<PaymentQuote['option'], string> = {
  save_return: 'Save & Return',
  finance: 'Financing',
  trade_in: 'Trade-in financing',
  outright: 'Buy outright',
};

@Component({
  selector: 'app-root',
  imports: [FormsModule, CurrencyPipe],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly chat = inject(ChatService);

  readonly messages = signal<Message[]>([
    { from: 'bot', text: "Hi! I'm the Rogers assistant. Tell me which phone you're after and how you use it." },
  ]);
  readonly sending = signal(false);
  readonly suggestions = [
    'I need iPhone 18 with unlimited calling',
    'iPhone 18 Pro, I use a lot of data',
    'Pixel 11 under $150 a month',
  ];

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

  paymentName(option: PaymentQuote['option']): string {
    return PAYMENT_NAMES[option] ?? option;
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
        const botMessages: Message[] = texts.map((t) => ({ from: 'bot', text: t }));
        // Show the option cards under the last part of the reply.
        if (reply.recommendations?.recommendations?.length) {
          botMessages[botMessages.length - 1].result = reply.recommendations;
        }
        this.messages.update((m) => [...m, ...botMessages]);
        this.sending.set(false);
      },
      error: (err) => {
        const detail = err?.error?.error ?? 'Could not reach the server. Is the backend running on port 3000?';
        this.messages.update((m) => [...m, { from: 'bot', text: detail, error: true }]);
        this.sending.set(false);
      },
    });
  }
}
