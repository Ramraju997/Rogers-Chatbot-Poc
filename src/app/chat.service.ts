import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatReply {
  messages: string[];
}

/**
 * Talks to our own backend (/api/chat), which in turn calls Dialogflow CX.
 * The browser never sees Google credentials.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  /** One session id per page load so Dialogflow keeps conversation context. */
  readonly sessionId = crypto.randomUUID();

  send(text: string): Observable<ChatReply> {
    return this.http.post<ChatReply>('/api/chat', {
      sessionId: this.sessionId,
      text,
    });
  }
}
