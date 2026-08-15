import type { Payment } from "./Payment";

export interface User {
  id: number;
  name: string;
  phoneNumber: string;
  createdBy: string;
  created: string; // ISO datetime
}

export interface CreateUserRequest {
  name: string;
  phoneNumber: string;
}

export interface UserPaymentSummary {
  user: User;
  totalPayments: number;
  totalLoans: number;
  totalDonations: number;
  totalAmount: number;
  payments: Payment[];
}
