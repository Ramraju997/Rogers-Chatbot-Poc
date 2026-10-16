import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PaymentQuote {
  option: 'save_return' | 'finance' | 'trade_in' | 'outright';
  label: string;
  deviceMonthly: number;
  totalMonthly: number;
  termMonths?: number;
  upfront?: number;
  returnCredit?: number;
  devicePriceId?: string;
}

export interface Recommendation {
  rank: number;
  planName: string;
  planId: string;
  data: string;
  calling?: string;
  planMonthly: number;
  payment: PaymentQuote;
  totalMonthly: number;
  otherPaymentOptions?: PaymentQuote[];
  cartUrl?: string;
}

export interface DeviceChoice {
  name: string;
  storage?: string;
  condition?: string;
  imageUrl?: string;
  label?: string;
  fromMonthly?: number;
  fromPaymentOption?: PaymentQuote['option'];
  fullPrice?: number;
}

export interface RecommendResult {
  status: string;
  /** Phones to pick from when the request matched several (status NEED_DEVICE_CHOICE). */
  deviceChoices?: DeviceChoice[];
  device?: {
    name: string;
    storage?: string;
    color?: string;
    condition?: string;
    simType?: string;
    imageUrl?: string;
    fullPricingAvailable: boolean;
  };
  recommendations?: Recommendation[];
  notes?: string[];
}

export interface ChatReply {
  messages: string[];
  /** Present when the agent used the recommendation tool for this reply. */
  recommendations?: RecommendResult;
}

/**
 * Talks to the Spring Boot backend (/api/chat), which calls Dialogflow CX and runs the
 * recommendation tool. The browser never sees Google credentials.
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
